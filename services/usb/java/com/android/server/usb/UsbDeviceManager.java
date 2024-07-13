/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions an
 * limitations under the License.
 */

package com.android.server.usb;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Debug;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UEventObserver;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Pair;
import android.util.Slog;
import android.util.Log;
import android.view.WindowManager;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.IndentingPrintWriter;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.server.FgThread;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

/**
 * UsbDeviceManager manages USB state in device mode.
 */
public class UsbDeviceManager {

    private static final String TAG = "UsbDeviceManager";
    private static final boolean DEBUG = true;

    /**
     * The persistent property which stores whether adb is enabled or not.
     * May also contain vendor-specific default functions for testing purposes.
     */
    private static final String USB_PERSISTENT_CONFIG_PROPERTY = "persist.sys.usb.config";

    /**
     * The non-persistent property which stores the current USB settings.
     */
    private static final String USB_CONFIG_PROPERTY = "sys.usb.config";

    /**
     * The non-persistent property which stores the current USB actual state.
     */
    private static final String USB_STATE_PROPERTY = "sys.usb.state";

    private static final String USB_STATE_MATCH =
            "DEVPATH=/devices/virtual/android_usb/android0";
    private static final String ACCESSORY_START_MATCH =
            "DEVPATH=/devices/virtual/misc/usb_accessory";
    private static final String FUNCTIONS_PATH =
            "/sys/class/android_usb/android0/functions";
    private static final String STATE_PATH =
            "/sys/class/android_usb/android0/state";
    private static final String RNDIS_ETH_ADDR_PATH =
            "/sys/class/android_usb/android0/f_rndis/ethaddr";
    private static final String AUDIO_SOURCE_PCM_PATH =
            "/sys/class/android_usb/android0/f_audio_source/pcm";
    private static final String MIDI_ALSA_PATH =
            "/sys/class/android_usb/android0/f_midi/alsa";

    private static final int MSG_UPDATE_STATE = 0;
    private static final int MSG_ENABLE_ADB = 1;
    private static final int MSG_SET_CURRENT_FUNCTIONS = 2;
    private static final int MSG_SYSTEM_READY = 3;
    private static final int MSG_BOOT_COMPLETED = 4;
    private static final int MSG_USER_SWITCHED = 5;
    private static final int MSG_SET_USB_DATA_UNLOCKED = 6;
    private static final int MSG_UPDATE_USER_RESTRICTIONS = 7;
    private static final int MSG_SIM_CHECKING = 8;
    private static final int MSG_LOCAL_CHANGE = 9;

    private static final int AUDIO_MODE_SOURCE = 1;

    // Delay for debouncing USB disconnects.
    // We often get rapid connect/disconnect events when enabling USB functions,
    // which need debouncing.
    private static final int UPDATE_DELAY = 1000;

    // Time we received a request to enter USB accessory mode
    private long mAccessoryModeRequestTime = 0;

    // Timeout for entering USB request mode.
    // Request is cancelled if host does not configure device within 10 seconds.
    private static final int ACCESSORY_REQUEST_TIMEOUT = 10 * 1000;

    private static final String BOOT_MODE_PROPERTY = "ro.bootmode";

    private UsbHandler mHandler;
    private boolean mBootCompleted;

    private final Object mLock = new Object();

    private final Context mContext;
    private final ContentResolver mContentResolver;
    @GuardedBy("mLock")
    private UsbSettingsManager mCurrentSettings;
    private NotificationManager mNotificationManager;
    private final boolean mHasUsbAccessory;
    private boolean mUseUsbNotification;
    private boolean mAdbEnabled;
    private boolean mAudioSourceEnabled;
    private boolean mMidiEnabled;
    private int mMidiCard;
    private int mMidiDevice;
    private Map<String, List<Pair<String, String>>> mOemModeMap;
    private String[] mAccessoryStrings;
    private UsbDebuggingManager mDebuggingManager;
    private final UsbAlsaManager mUsbAlsaManager;

    // SPRD: Add for sprd usb common functions
    private boolean mPowerDisconnected = false;
    private static final int UPDATE_DELAY_MORE = 20000;

    // SPRD: Add for usb activated feature.
    private static final int MSG_SWITCH_FOR_USB_ACTIVE_CHANGED = 10;
    private final static String USB_ACTIVATED = "usb_actived";
    private AlertDialog mWarningDialog = null;
    private static final int SIM_CHECKING_TIMEOUT = 5000;
    private boolean isSimChecking = true;
    private boolean isSimStateRegister = false;

    // SPRD： Add for security lock enabled feature.
    private final static boolean isSecurityLockEnabled = SystemProperties.getBoolean("persist.sys.usb.security_lock", false);
    private boolean isLock = true; // The screen is locked default, and isLocked should be change from broadcast.
    private String mRememberedFunctions = null;

    // SPRD: Add for ums function
    private boolean isUmsShared = false;

    private boolean orignal_support = SystemProperties.getBoolean("ro.usb.orignal.design", false);
    private boolean cta_support = SystemProperties.getBoolean("ro.usb.support_cta", false);

    // SPRD: Broadcast of usb state changed should be sent after system ready
    private boolean mSystemReady = false;

    private class AdbSettingsObserver extends ContentObserver {
        public AdbSettingsObserver() {
            super(null);
        }
        @Override
        public void onChange(boolean selfChange) {
            boolean enable = (Settings.Global.getInt(mContentResolver,
                    Settings.Global.ADB_ENABLED, 0) > 0);
            mHandler.sendMessage(MSG_ENABLE_ADB, enable);
        }
    }

    private class UsbActiveSwitchSettingsObserver extends ContentObserver {
        public UsbActiveSwitchSettingsObserver() {
            super(null);
        }
        @Override
        public void onChange(boolean selfChange) {
            boolean enable = (Settings.Global.getInt(mContentResolver,
                    Settings.Global.SWITCH_FOR_USB_ACTIVE, 0) > 0);
            mHandler.sendMessage(MSG_SWITCH_FOR_USB_ACTIVE_CHANGED, enable);
        }
    }

    /*
     * Listens for uevent messages from the kernel to monitor the USB state
     */
    private final UEventObserver mUEventObserver = new UEventObserver() {
        @Override
        public void onUEvent(UEventObserver.UEvent event) {
            if (DEBUG) Slog.v(TAG, "USB UEVENT: " + event.toString());

            String state = event.get("USB_STATE");
            String accessory = event.get("ACCESSORY");
            if (state != null) {
                mHandler.updateState(state);
            } else if ("START".equals(accessory)) {
                if (DEBUG) Slog.d(TAG, "got accessory start");
                startAccessoryMode();
            }
        }
    };

    public UsbDeviceManager(Context context, UsbAlsaManager alsaManager) {
        mContext = context;
        mUsbAlsaManager = alsaManager;
        mContentResolver = context.getContentResolver();
        PackageManager pm = mContext.getPackageManager();
        mHasUsbAccessory = pm.hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY);
        initRndisAddress();

        readOemUsbOverrideConfig();

        mHandler = new UsbHandler(FgThread.get().getLooper());

        if (nativeIsStartRequested()) {
            if (DEBUG) Slog.d(TAG, "accessory attached at boot");
            startAccessoryMode();
        }

        boolean secureAdbEnabled = SystemProperties.getBoolean("ro.adb.secure", false);
        boolean dataEncrypted = "1".equals(SystemProperties.get("vold.decrypt"));
        if (secureAdbEnabled && !dataEncrypted) {
            mDebuggingManager = new UsbDebuggingManager(context);
        }
    }

    private UsbSettingsManager getCurrentSettings() {
        synchronized (mLock) {
            return mCurrentSettings;
        }
    }

    public void systemReady() {
        if (DEBUG) Slog.d(TAG, "systemReady");

        mNotificationManager = (NotificationManager)
                mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        // We do not show the USB notification if the primary volume supports mass storage.
        // The legacy mass storage UI will be used instead.
        boolean massStorageSupported = false;
        final StorageManager storageManager = StorageManager.from(mContext);
        final StorageVolume primary = storageManager.getPrimaryVolume();
        massStorageSupported = primary != null && primary.allowMassStorage();
        mUseUsbNotification = !massStorageSupported;

        // make sure the ADB_ENABLED setting value matches the current state
        try {
            Settings.Global.putInt(mContentResolver,
                    Settings.Global.ADB_ENABLED, mAdbEnabled ? 1 : 0);
        } catch (SecurityException e) {
            // If UserManager.DISALLOW_DEBUGGING_FEATURES is on, that this setting can't be changed.
            Slog.d(TAG, "ADB_ENABLED is restricted.");
        }

        if (Settings.Global.getInt(mContentResolver,
                Settings.Global.USB_REMEMBER_CHOICE, 0) == 0) {
            Settings.Global.putInt(mContentResolver,
                    Settings.Global.USB_CURRENT_FUNCTION, 5);
            Settings.Global.putInt(mContentResolver,
                    Settings.Global.USB_REMEMBER_CHOICE, 1);
            
        }
        mHandler.sendEmptyMessage(MSG_SYSTEM_READY);
    }

    public void bootCompleted() {
        if (DEBUG) Slog.d(TAG, "boot completed");
        mHandler.sendEmptyMessage(MSG_BOOT_COMPLETED);
    }

    public void setCurrentUser(int userId, UsbSettingsManager settings) {
        synchronized (mLock) {
            mCurrentSettings = settings;
            mHandler.obtainMessage(MSG_USER_SWITCHED, userId, 0).sendToTarget();
        }
    }

    public void updateUserRestrictions() {
        mHandler.sendEmptyMessage(MSG_UPDATE_USER_RESTRICTIONS);
    }

    private void startAccessoryMode() {
        if (!mHasUsbAccessory) return;

        mAccessoryStrings = nativeGetAccessoryStrings();
        boolean enableAudio = (nativeGetAudioMode() == AUDIO_MODE_SOURCE);
        // don't start accessory mode if our mandatory strings have not been set
        boolean enableAccessory = (mAccessoryStrings != null &&
                        mAccessoryStrings[UsbAccessory.MANUFACTURER_STRING] != null &&
                        mAccessoryStrings[UsbAccessory.MODEL_STRING] != null);
        String functions = null;

        if (enableAccessory && enableAudio) {
            functions = UsbManager.USB_FUNCTION_ACCESSORY + ","
                    + UsbManager.USB_FUNCTION_AUDIO_SOURCE;
        } else if (enableAccessory) {
            functions = UsbManager.USB_FUNCTION_ACCESSORY;
        } else if (enableAudio) {
            functions = UsbManager.USB_FUNCTION_AUDIO_SOURCE;
        }

        if (functions != null) {
            mAccessoryModeRequestTime = SystemClock.elapsedRealtime();
            setCurrentFunctions(functions);
        }
    }

    private static void initRndisAddress() {
        // configure RNDIS ethernet address based on our serial number using the same algorithm
        // we had been previously using in kernel board files
        final int ETH_ALEN = 6;
        int address[] = new int[ETH_ALEN];
        // first byte is 0x02 to signify a locally administered address
        address[0] = 0x02;

        String serial = SystemProperties.get("ro.serialno", "1234567890ABCDEF");
        int serialLength = serial.length();
        // XOR the USB serial across the remaining 5 bytes
        for (int i = 0; i < serialLength; i++) {
            address[i % (ETH_ALEN - 1) + 1] ^= (int)serial.charAt(i);
        }
        String addrString = String.format(Locale.US, "%02X:%02X:%02X:%02X:%02X:%02X",
            address[0], address[1], address[2], address[3], address[4], address[5]);
        try {
            FileUtils.stringToFile(RNDIS_ETH_ADDR_PATH, addrString);
        } catch (IOException e) {
           Slog.e(TAG, "failed to write to " + RNDIS_ETH_ADDR_PATH);
        }
    }

    private final class UsbHandler extends Handler {

        // current USB state
        private boolean mConnected;
        private boolean mConfigured;
        private boolean mUsbDataUnlocked;
        private String mCurrentFunctions;
        private boolean mCurrentFunctionsApplied;
        private UsbAccessory mCurrentAccessory;
        private int mUsbNotificationId;
        private boolean mAdbNotificationShown;
        private int mCurrentUser = UserHandle.USER_NULL;

        private final BroadcastReceiver mPowerStateChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (DEBUG) Slog.d(TAG, "action = " + action);
                if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                    mPowerDisconnected = true;
                    if (mNotificationManager != null && mUsbNotificationId != 0) {
                        mNotificationManager.cancelAsUser(null, mUsbNotificationId,
                                UserHandle.ALL);
                        mUsbNotificationId = 0;
                    }
                    update();
                } else {
                    mPowerDisconnected = false;
                    updateUsbNotification();
                    updateAdbNotification();
                }
            }

            /**
             * If power disconnected, update to handle usb disconnected event immediately
             */
            private void update() {
                // TODO Auto-generated method stub
                removeMessages(MSG_UPDATE_STATE);
                Message msg = Message.obtain(mHandler, MSG_UPDATE_STATE);
                msg.arg1 = 0;
                msg.arg2 = 0;
                sendMessageDelayed(msg, 0);
            }
        };

        private final BroadcastReceiver mLockUnlockChangeReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (DEBUG) Slog.d(TAG, "action = " + action);
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    isLock = true;
                } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                    isLock = false;
                    //TODO -- set the remembered function
                    if (UsbManager.USB_FUNCTION_NONE.equals(mCurrentFunctions)
                            && !UsbManager.USB_FUNCTION_NONE.equals(mRememberedFunctions)) {
                        setEnabledFunctions(mRememberedFunctions, false);
                        setUsbDataUnlocked(true); // must, enum may not occur if not
                    }
                }
            }
        };

        private final BroadcastReceiver mLanguageChangeReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (DEBUG)
                    Slog.d(TAG, "action = " + action);
                if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
                    Message msg = Message.obtain(mHandler, MSG_LOCAL_CHANGE);
                    sendMessageDelayed(msg, 0);
                }
            }
        };

        private final BroadcastReceiver mUmsStateChangeReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (DEBUG) Slog.d(TAG, "action = " + action);
                if (Intent.ACTION_MEDIA_SHARED.equals(action)) {
                    isUmsShared = true;
                } else {
                    if ((!Environment.MEDIA_SHARED.equals(Environment.getInternalStoragePathState()))
                            && (!Environment.MEDIA_SHARED.equals(Environment.getExternalStoragePathState()))) {
                        isUmsShared = false;
                    }
                }
                updateUsbNotification();
            }
        };

        private final BroadcastReceiver mSimStateChangeReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String stateExtra = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                if (DEBUG) Slog.d(TAG, TelephonyIntents.ACTION_SIM_STATE_CHANGED + stateExtra);
                if (!IccCardConstants.INTENT_VALUE_ICC_UNKNOWN.equals(stateExtra)
                        && !IccCardConstants.INTENT_VALUE_ICC_NOT_READY.equals(stateExtra)
                        && !IccCardConstants.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
                    if (mWarningDialog != null) {
                        mWarningDialog.dismiss();
                    }
                    isSimChecking = false;
                    isSimStateRegister = false;
                    Settings.Global.putInt(mContentResolver, USB_ACTIVATED, 1);
                    setEnabledFunctions(null, false);
                    mContext.unregisterReceiver(mSimStateChangeReceiver);
                }
            }
        };

        public UsbHandler(Looper looper) {
            super(looper);
            try {
                // Restore default functions.
                mCurrentFunctions = SystemProperties.get(USB_CONFIG_PROPERTY,
                        UsbManager.USB_FUNCTION_NONE);
                if (UsbManager.USB_FUNCTION_NONE.equals(mCurrentFunctions)) {
                    mCurrentFunctions = UsbManager.USB_FUNCTION_MTP;
                }
                mCurrentFunctionsApplied = mCurrentFunctions.equals(
                        SystemProperties.get(USB_STATE_PROPERTY));
                mAdbEnabled = UsbManager.containsFunction(getDefaultFunctions(),
                        UsbManager.USB_FUNCTION_ADB);
                setEnabledFunctions(null, false);

                if (isUsbShouldActived()) {
                    IntentFilter simFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
                    mContext.registerReceiver(mSimStateChangeReceiver, simFilter);
                    isSimStateRegister = true;
                }
                String state = FileUtils.readTextFile(new File(STATE_PATH), 0, null).trim();
                updateState(state);

                // register observer to listen for settings changes
                mContentResolver.registerContentObserver(
                        Settings.Global.getUriFor(Settings.Global.SWITCH_FOR_USB_ACTIVE),
                                false, new UsbActiveSwitchSettingsObserver());

                // register observer to listen for settings changes
                mContentResolver.registerContentObserver(
                        Settings.Global.getUriFor(Settings.Global.ADB_ENABLED),
                                false, new AdbSettingsObserver());

                // Watch for USB configuration changes
                mUEventObserver.startObserving(USB_STATE_MATCH);
                mUEventObserver.startObserving(ACCESSORY_START_MATCH);

                IntentFilter umsStateFilter = new IntentFilter();
                IntentFilter languageChangeFilter = new IntentFilter();
                languageChangeFilter.addAction(Intent.ACTION_LOCALE_CHANGED);
                umsStateFilter.addAction(Intent.ACTION_MEDIA_SHARED);
                umsStateFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
                umsStateFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
                umsStateFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
                umsStateFilter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
                umsStateFilter.addAction(Intent.ACTION_MEDIA_NOFS);
                umsStateFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTABLE);
                umsStateFilter.addDataScheme("file");
                mContext.registerReceiver(mUmsStateChangeReceiver, umsStateFilter);

                if (isSecurityLockEnabled) {
                    IntentFilter lockUnlockFilter = new IntentFilter();
                    lockUnlockFilter.addAction(Intent.ACTION_SCREEN_OFF);
                    lockUnlockFilter.addAction(Intent.ACTION_USER_PRESENT);
                    mContext.registerReceiver(mLockUnlockChangeReceiver, lockUnlockFilter);
                }

                IntentFilter powerStateFilter = new IntentFilter();
                powerStateFilter.addAction(Intent.ACTION_POWER_CONNECTED);
                powerStateFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
                mContext.registerReceiver(mPowerStateChangedReceiver, powerStateFilter);
                mContext.registerReceiver(mLanguageChangeReceiver, languageChangeFilter);
            } catch (Exception e) {
                Slog.e(TAG, "Error initializing UsbHandler", e);
            }
        }

        public void sendMessage(int what, boolean arg) {
            removeMessages(what);
            Message m = Message.obtain(this, what);
            m.arg1 = (arg ? 1 : 0);
            sendMessage(m);
        }

        public void sendMessage(int what, Object arg) {
            removeMessages(what);
            Message m = Message.obtain(this, what);
            m.obj = arg;
            sendMessage(m);
        }

        public void updateState(String state) {
            int connected, configured;

            if ("DISCONNECTED".equals(state)) {
                connected = 0;
                configured = 0;
            } else if ("CONNECTED".equals(state)) {
                connected = 1;
                configured = 0;
            } else if ("CONFIGURED".equals(state)) {
                connected = 1;
                configured = 1;
            } else {
                Slog.e(TAG, "unknown state " + state);
                return;
            }

            removeMessages(MSG_UPDATE_STATE);
            Message msg = Message.obtain(this, MSG_UPDATE_STATE);
            msg.arg1 = connected;
            msg.arg2 = configured;
            // debounce disconnects to avoid problems bringing up USB tethering
            if (UsbManager.USB_FUNCTION_NONE.equals(mCurrentFunctions)) {
                sendMessageDelayed(msg, 0);
            } else {
                sendMessageDelayed(msg, (connected == 0) ? UPDATE_DELAY_MORE : 0);
           }
        }

        private boolean waitForState(String state) {
            // wait for the transition to complete.
            // give up after 1 second.
            String value = null;
            for (int i = 0; i < 20; i++) {
                // State transition is done when sys.usb.state is set to the new configuration
                value = SystemProperties.get(USB_STATE_PROPERTY);
                if (state.equals(value)) return true;
                SystemClock.sleep(50);
            }
            Slog.e(TAG, "waitForState(" + state + ") FAILED: got " + value);
            return false;
        }

        private boolean setUsbConfig(String config) {
            if (DEBUG) Slog.d(TAG, "setUsbConfig(" + config + ")");
            // set the new configuration
            String oldConfig = SystemProperties.get(USB_CONFIG_PROPERTY);
            if (!config.equals(oldConfig)) {
                SystemProperties.set(USB_CONFIG_PROPERTY, config);
            }
            return waitForState(config);
        }

        private void setUsbDataUnlocked(boolean enable) {
            if (DEBUG) Slog.d(TAG, "setUsbDataUnlocked: " + enable);
            mUsbDataUnlocked = enable;
            updateUsbNotification();
            // SPRD: Delete it for Bug 513289
            //updateUsbStateBroadcast();
            setEnabledFunctions(mCurrentFunctions, true);
        }

        private void setAdbEnabled(boolean enable) {
            if (DEBUG) Slog.d(TAG, "setAdbEnabled: " + enable);
            if (enable != mAdbEnabled) {
                mAdbEnabled = enable;

                if (UsbManager.USB_FUNCTION_NONE.equals(mCurrentFunctions) ) {
                        Slog.d(TAG, "adb state is changed, But the mCurrentFunctions is none, do nothing!");
                } else {
                    // Due to the persist.sys.usb.config property trigger, changing adb state requires
                    // persisting default function
                    String oldFunctions = getDefaultFunctions();
                    String newFunctions = applyAdbFunction(oldFunctions);
                    if (!oldFunctions.equals(newFunctions)) {
                        SystemProperties.set(USB_PERSISTENT_CONFIG_PROPERTY, newFunctions);
                    }
                    // After persisting them use the lock-down aware function set
                    setEnabledFunctions(mCurrentFunctions, false);
                    updateAdbNotification();
                }
            }

            if (mDebuggingManager != null) {
                mDebuggingManager.setAdbEnabled(mAdbEnabled);
            }
        }

        /**
         * Evaluates USB function policies and applies the change accordingly.
         */
        private void setEnabledFunctions(String functions, boolean forceRestart) {
            if (DEBUG) Slog.d(TAG, "setEnabledFunctions functions=" + functions + ", "
                    + "forceRestart=" + forceRestart);

            // Try to set the enabled functions.
            final String oldFunctions = mCurrentFunctions;
            final boolean oldFunctionsApplied = mCurrentFunctionsApplied;
            if (trySetEnabledFunctions(functions, forceRestart)) {
                return;
            }

            // Didn't work.  Try to revert changes.
            // We always reapply the policy in case certain constraints changed such as
            // user restrictions independently of any other new functions we were
            // trying to activate.
            if (oldFunctionsApplied && !oldFunctions.equals(functions)) {
                Slog.e(TAG, "Failsafe 1: Restoring previous USB functions.");
                if (trySetEnabledFunctions(oldFunctions, false)) {
                    return;
                }
            }

            // Still didn't work.  Try to restore the default functions.
            Slog.e(TAG, "Failsafe 2: Restoring default USB functions.");
            if (trySetEnabledFunctions(null, false)) {
                return;
            }

            // Now we're desperate.  Ignore the default functions.
            // Try to get ADB working if enabled.
            Slog.e(TAG, "Failsafe 3: Restoring empty function list (with ADB if enabled).");
            if (trySetEnabledFunctions(UsbManager.USB_FUNCTION_NONE, false)) {
                return;
            }

            // Ouch.
            Slog.e(TAG, "Unable to set any USB functions!");
        }

        private boolean trySetEnabledFunctions(String functions, boolean forceRestart) {
            Slog.i(TAG, "Trying to setting USB config to " + functions + ", mCurrentFunctions = " + mCurrentFunctions);
            boolean isLastNone = false;
            if (UsbManager.USB_FUNCTION_NONE.equals(mCurrentFunctions)) {
                isLastNone = true;
            }
            if (UsbManager.USB_FUNCTION_NONE.equals(functions)) {
                if (!isLastNone) {
                    mCurrentFunctions = functions;
                    setUsbConfig(UsbManager.USB_FUNCTION_NONE);
                }
                return true;
            }

            if (functions == null) {
                functions = getDefaultFunctions();
            }
            functions = applyAdbFunction(functions);
            functions = applyOemOverrideFunction(functions);

            if (!mCurrentFunctions.equals(functions) || !mCurrentFunctionsApplied
                    || forceRestart) {
                Slog.i(TAG, "Setting USB config to " + functions);
                mCurrentFunctions = functions;
                mCurrentFunctionsApplied = false;

                // Kick the USB stack to close existing connections.
                if (!isLastNone) setUsbConfig(UsbManager.USB_FUNCTION_NONE);

                // Set the new USB configuration.
                if (!setUsbConfig(functions)) {
                    Slog.e(TAG, "Failed to switch USB config to " + functions);
                    return false;
                }

                mCurrentFunctionsApplied = true;
            }
            return true;
        }

        private String applyAdbFunction(String functions) {
            if (UsbManager.USB_FUNCTION_NONE.equals(functions)) {
                return UsbManager.USB_FUNCTION_NONE;
            }

            if (mAdbEnabled) {
                functions = UsbManager.addFunction(functions, UsbManager.USB_FUNCTION_ADB);
            } else {
                functions = UsbManager.removeFunction(functions, UsbManager.USB_FUNCTION_ADB);
            }
            return functions;
        }

        private boolean isUsbTransferAllowed() {
            UserManager userManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
            return !userManager.hasUserRestriction(UserManager.DISALLOW_USB_FILE_TRANSFER);
        }

        private void updateCurrentAccessory() {
            // We are entering accessory mode if we have received a request from the host
            // and the request has not timed out yet.
            boolean enteringAccessoryMode =
                    mAccessoryModeRequestTime > 0 &&
                        SystemClock.elapsedRealtime() <
                            mAccessoryModeRequestTime + ACCESSORY_REQUEST_TIMEOUT;

            if (mConfigured && enteringAccessoryMode) {
                // successfully entered accessory mode

                if (mAccessoryStrings != null) {
                    mCurrentAccessory = new UsbAccessory(mAccessoryStrings);
                    Slog.d(TAG, "entering USB accessory mode: " + mCurrentAccessory);
                    // defer accessoryAttached if system is not ready
                    if (mBootCompleted) {
                        getCurrentSettings().accessoryAttached(mCurrentAccessory);
                    } // else handle in boot completed
                } else {
                    Slog.e(TAG, "nativeGetAccessoryStrings failed");
                }
            } else if (!enteringAccessoryMode) {
                // make sure accessory mode is off
                // and restore default functions
                Slog.d(TAG, "exited USB accessory mode");
                setEnabledFunctions(null, false);

                if (mCurrentAccessory != null) {
                    if (mBootCompleted) {
                        getCurrentSettings().accessoryDetached(mCurrentAccessory);
                    }
                    mCurrentAccessory = null;
                    mAccessoryStrings = null;
                }
            }
        }

        // SPRD: add for checkout the state of sim card
        private boolean isSimCardInserted() {
            TelephonyManager tm = TelephonyManager.from(mContext);
            int num = tm.getPhoneCount();
            for (int i = 0; i < num; i++) {
                if (tm.hasIccCard(i)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isUsbShouldActived() {
            return isUsbSwitchEnabled() && isUsbNotActivated();
        }

        // SPRD: add for checkout whether usb_activite_switch is opened.
        private boolean isUsbSwitchEnabled() {
            return Settings.Global.getInt(mContentResolver, Settings.Global.SWITCH_FOR_USB_ACTIVE, 0) == 1;
        }

        // SPRD: add for read the mark to checkout whether usb has been activated
        private boolean isUsbNotActivated() {
            return Settings.Global.getInt(mContentResolver, USB_ACTIVATED, 0) == 0;
        }

        // SPRD: add for show Dialog to remind the user to activate usb
        private void showWarningDialog() {
            if (mWarningDialog == null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
                .setMessage(com.android.internal.R.string.sim_card_activation_warning)
                .setTitle(com.android.internal.R.string.sim_ready_title_for_usb)
                .setPositiveButton(com.android.internal.R.string.usb_ok,
                        new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog,
                            int witch) {
                        // TODO Auto-generated method stub
                        dialog.dismiss();
                    }
                });
                mWarningDialog = builder.create();
                mWarningDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                mWarningDialog.setCanceledOnTouchOutside(false);
            }
            if (!mWarningDialog.isShowing()) {
                mWarningDialog.show();
            }
        }

        private void updateUsbStateBroadcast() {

            if (mConnected && isUsbShouldActived() && !isSimChecking) {
                showWarningDialog();
                return;
            }

            // send a sticky broadcast containing current USB state
            Intent intent = new Intent(UsbManager.ACTION_USB_STATE);
            intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING
                    | Intent.FLAG_RECEIVER_FOREGROUND);
            intent.putExtra(UsbManager.USB_CONNECTED, mConnected);
            intent.putExtra(UsbManager.USB_CONFIGURED, mConfigured);
            intent.putExtra(UsbManager.USB_DATA_UNLOCKED, isUsbTransferAllowed()/* && mUsbDataUnlocked);*/
                    && (mUsbDataUnlocked || cta_support));

            if (mCurrentFunctions != null) {
                String[] functions = mCurrentFunctions.split(",");
                for (int i = 0; i < functions.length; i++) {
                    intent.putExtra(functions[i], true);
                }
            }

            if (DEBUG) Slog.d(TAG, "broadcasting " + intent + " connected: " + mConnected
                                    + " configured: " + mConfigured);
            mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }

        private void updateUsbFunctions() {
            updateAudioSourceFunction();
            updateMidiFunction();
        }

        private void updateAudioSourceFunction() {
            boolean enabled = UsbManager.containsFunction(mCurrentFunctions,
                    UsbManager.USB_FUNCTION_AUDIO_SOURCE);
            if (enabled != mAudioSourceEnabled) {
                int card = -1;
                int device = -1;

                if (enabled) {
                    Scanner scanner = null;
                    try {
                        scanner = new Scanner(new File(AUDIO_SOURCE_PCM_PATH));
                        card = scanner.nextInt();
                        device = scanner.nextInt();
                    } catch (FileNotFoundException e) {
                        Slog.e(TAG, "could not open audio source PCM file", e);
                    } finally {
                        if (scanner != null) {
                            scanner.close();
                        }
                    }
                }
                mUsbAlsaManager.setAccessoryAudioState(enabled, card, device);
                mAudioSourceEnabled = enabled;
            }
        }

        private void updateMidiFunction() {
            boolean enabled = UsbManager.containsFunction(mCurrentFunctions,
                    UsbManager.USB_FUNCTION_MIDI);
            if (enabled != mMidiEnabled) {
                if (enabled) {
                    Scanner scanner = null;
                    try {
                        scanner = new Scanner(new File(MIDI_ALSA_PATH));
                        mMidiCard = scanner.nextInt();
                        mMidiDevice = scanner.nextInt();
                    } catch (FileNotFoundException e) {
                        Slog.e(TAG, "could not open MIDI PCM file", e);
                        enabled = false;
                    } finally {
                        if (scanner != null) {
                            scanner.close();
                        }
                    }
                }
                mMidiEnabled = enabled;
            }
            mUsbAlsaManager.setPeripheralMidiState(mMidiEnabled && mConfigured, mMidiCard, mMidiDevice);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_STATE:
                    mConnected = (msg.arg1 == 1);
                    mConfigured = (msg.arg2 == 1);
                    if (!mConnected && orignal_support) {
                        // When a disconnect occurs, relock access to sensitive user data
                        mUsbDataUnlocked = false;
                    }
                    if (UsbManager.containsFunction(mCurrentFunctions,
                            UsbManager.USB_FUNCTION_ACCESSORY)) {
                        updateCurrentAccessory();
                    } else if (!mConnected &&
                            (orignal_support || UsbManager.containsFunction(mCurrentFunctions, UsbManager.USB_FUNCTION_RNDIS))) {
                        // restore defaults when USB is disconnected
                        setEnabledFunctions(null, false); //Cover the current function with defaults when tethering and disconnected
                    }
                    if (mBootCompleted || mSystemReady) {
                        if ((mConnected && isUsbShouldActived() && !isSimChecking) //usb should actived
                               || (isSecurityLockEnabled && mConnected && isLock)) { // Set none if screen is locked.
                            setEnabledFunctions(UsbManager.USB_FUNCTION_NONE, false);
                        }
                        updateUsbStateBroadcast();
                        updateUsbFunctions();
                    }
                    updateUsbNotification();
                    updateAdbNotification();
                    break;
                case MSG_ENABLE_ADB:
                    setAdbEnabled(msg.arg1 == 1);
                    break;
                case MSG_SET_CURRENT_FUNCTIONS:
                    String functions = (String)msg.obj;
                    setEnabledFunctions(functions, false);
                    break;
                case MSG_UPDATE_USER_RESTRICTIONS:
                    setEnabledFunctions(mCurrentFunctions, false);
                    break;
                case MSG_SET_USB_DATA_UNLOCKED:
                    setUsbDataUnlocked(msg.arg1 == 1);
                    break;
                case MSG_SYSTEM_READY:
                    mSystemReady = true;
                    updateUsbNotification();
                    updateAdbNotification();
                    updateUsbStateBroadcast();
                    updateUsbFunctions();
                    if (isSecurityLockEnabled && mConnected && isLock/* && !UsbManager.USB_FUNCTION_NONE.equals(mCurrentFunctions)*/) { //Usb should not be used when screen locked
                        setEnabledFunctions(UsbManager.USB_FUNCTION_NONE, false);
                    }
                    break;
                case MSG_BOOT_COMPLETED:
                    mBootCompleted = true;
                    if (mCurrentAccessory != null) {
                        getCurrentSettings().accessoryAttached(mCurrentAccessory);
                    }
                    if (mDebuggingManager != null) {
                        mDebuggingManager.setAdbEnabled(mAdbEnabled);
                    }
                    if (isUsbShouldActived()) {
                        sendMessageDelayed(Message.obtain(mHandler, MSG_SIM_CHECKING), SIM_CHECKING_TIMEOUT);
                    }
                    break;
                case MSG_USER_SWITCHED: {
                    if (mCurrentUser != msg.arg1) {
                        // Restart the USB stack and re-apply user restrictions for MTP or PTP.
                        final boolean active = UsbManager.containsFunction(mCurrentFunctions,
                                        UsbManager.USB_FUNCTION_MTP)
                                || UsbManager.containsFunction(mCurrentFunctions,
                                        UsbManager.USB_FUNCTION_PTP);
                        if (active && mCurrentUser != UserHandle.USER_NULL) {
                            Slog.v(TAG, "Current user switched to " + mCurrentUser
                                    + "; resetting USB host stack for MTP or PTP");
                            setEnabledFunctions(mCurrentFunctions, true);
                        }
                        mCurrentUser = msg.arg1;
                    }
                    break;
                }
                case MSG_SIM_CHECKING:
                    isSimChecking = false;
                    updateUsbNotification();
                    updateAdbNotification();
                    if (mConnected && isUsbShouldActived()) {
                        setEnabledFunctions(UsbManager.USB_FUNCTION_NONE, false);
                        showWarningDialog();
                    }
                    break;
                case MSG_LOCAL_CHANGE:
                    mUsbNotificationId = 0;
                    mAdbNotificationShown = false;
                    updateUsbNotification();
                    updateAdbNotification();
                    break;
                case MSG_SWITCH_FOR_USB_ACTIVE_CHANGED:
                    if (msg.arg1 == 1) {
                        isSimChecking = false;
                        if (mConnected && isUsbShouldActived()) {
                            setEnabledFunctions(UsbManager.USB_FUNCTION_NONE, false);
                            showWarningDialog();
                        }
                        if (isUsbShouldActived() && isSimStateRegister == false) {
                            IntentFilter simFilter = new IntentFilter(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
                            mContext.registerReceiver(mSimStateChangeReceiver, simFilter);
                            isSimStateRegister = true;
                        }
                    } else {
                        if (mWarningDialog != null) {
                            mWarningDialog.dismiss();
                            setEnabledFunctions(null, false);
                        }
                        if (isSimStateRegister) {
                            mContext.unregisterReceiver(mSimStateChangeReceiver);
                            isSimStateRegister = false;
                        }
                    }
                    break;
            }
        }

        public UsbAccessory getCurrentAccessory() {
            return mCurrentAccessory;
        }

        private void updateUsbNotification() {
            if (mNotificationManager == null || !mUseUsbNotification || mPowerDisconnected) return;

            // Clear usb notification when usb should be actived.
            if (isUsbShouldActived()) {
                if (mUsbNotificationId != 0) {
                    mNotificationManager.cancelAsUser(null, mUsbNotificationId, UserHandle.ALL);
                    mUsbNotificationId = 0;
                }
                return;
            }

            int id = 0;
            Resources r = mContext.getResources();
            if (mConnected) {
                if (UsbManager.containsFunction(mCurrentFunctions,
                        UsbManager.USB_FUNCTION_CDROM)) {
                    id = com.android.internal.R.string.usb_cdrom_notification_title;
                } else if (UsbManager.containsFunction(mCurrentFunctions,
                        UsbManager.USB_FUNCTION_RNDIS)) {
                    // Usb tethering notification will show in Tethering.
                    if (mUsbNotificationId != 0) {
                        mNotificationManager.cancelAsUser(null, mUsbNotificationId,
                                UserHandle.ALL);
                        mUsbNotificationId = 0;
                    }
                    return;
                } else if (isUmsShared) {
                    id = com.android.internal.R.string.usb_storage_stop_title;
                } else if (!mUsbDataUnlocked && (orignal_support || !cta_support)) {
                    id = com.android.internal.R.string.usb_charging_notification_title;
                } else if (UsbManager.containsFunction(mCurrentFunctions,
                        UsbManager.USB_FUNCTION_MTP)) {
                    id = com.android.internal.R.string.usb_mtp_notification_title;
                } else if (UsbManager.containsFunction(mCurrentFunctions,
                        UsbManager.USB_FUNCTION_PTP)) {
                    id = com.android.internal.R.string.usb_ptp_notification_title;
                } else if (UsbManager.containsFunction(mCurrentFunctions,
                        UsbManager.USB_FUNCTION_MIDI)) {
                    id = com.android.internal.R.string.usb_midi_notification_title;
                } else if (UsbManager.containsFunction(mCurrentFunctions,
                        UsbManager.USB_FUNCTION_ACCESSORY)) {
                    id = com.android.internal.R.string.usb_accessory_notification_title;
                } else {
                    id = com.android.internal.R.string.usb_charging_notification_title;
                }
            } else if (UsbManager.USB_FUNCTION_NONE.equals(mCurrentFunctions)) { // SPRD: add for sprd usb charge function
                id = com.android.internal.R.string.usb_charging_notification_title;
            }
            if (id != mUsbNotificationId) {
                // clear notification if title needs changing
                if (mUsbNotificationId != 0) {
                    mNotificationManager.cancelAsUser(null, mUsbNotificationId,
                            UserHandle.ALL);
                    mUsbNotificationId = 0;
                }
                if (id != 0) {
                    CharSequence message = r.getText(
                            com.android.internal.R.string.usb_notification_message);
                    CharSequence title = r.getText(id);

                    Intent intent = Intent.makeRestartActivityTask(
                            new ComponentName("com.android.settings",
                            orignal_support ? "com.android.settings.deviceinfo.UsbModeChooserActivity" : "com.sprd.settings.SprdUsbSettings"));
                    PendingIntent pi = PendingIntent.getActivityAsUser(mContext, 0,
                            intent, 0, null, UserHandle.CURRENT);

                    Notification notification = new Notification.Builder(mContext)
                            .setSmallIcon(com.android.internal.R.drawable.stat_sys_data_usb)
                            .setWhen(0)
                            .setOngoing(true)
                            .setTicker(title)
                            .setDefaults(0)  // please be quiet
                            .setPriority(Notification.PRIORITY_MIN)
                            .setColor(mContext.getColor(
                                    com.android.internal.R.color.system_notification_accent_color))
                            .setContentTitle(title)
                            .setContentText(message)
                            .setContentIntent(pi)
                            .setVisibility(Notification.VISIBILITY_PUBLIC)
                            .build();
                    mNotificationManager.notifyAsUser(null, id, notification,
                            UserHandle.ALL);
                    mUsbNotificationId = id;
                }
            }
        }

        private void updateAdbNotification() {
            if (mNotificationManager == null) return;
            final int id = com.android.internal.R.string.adb_active_notification_title;
            if (mAdbEnabled && mConnected && !UsbManager.USB_FUNCTION_NONE.equals(mCurrentFunctions)) {
                if ("0".equals(SystemProperties.get("persist.adb.notify"))) return;

                if (!mAdbNotificationShown) {
                    Resources r = mContext.getResources();
                    CharSequence title = r.getText(id);
                    CharSequence message = r.getText(
                            com.android.internal.R.string.adb_active_notification_message);

                    Intent intent = Intent.makeRestartActivityTask(
                            new ComponentName("com.android.settings",
                                    "com.android.settings.DevelopmentSettings"));
                    PendingIntent pi = PendingIntent.getActivityAsUser(mContext, 0,
                            intent, 0, null, UserHandle.CURRENT);

                    Notification notification = new Notification.Builder(mContext)
                            .setSmallIcon(com.android.internal.R.drawable.stat_sys_adb)
                            .setWhen(0)
                            .setOngoing(true)
                            .setTicker(title)
                            .setDefaults(0)  // please be quiet
                            .setPriority(Notification.PRIORITY_LOW)
                            .setColor(mContext.getColor(
                                    com.android.internal.R.color.system_notification_accent_color))
                            .setContentTitle(title)
                            .setContentText(message)
                            .setContentIntent(pi)
                            .setVisibility(Notification.VISIBILITY_PUBLIC)
                            .build();
                    mAdbNotificationShown = true;
                    mNotificationManager.notifyAsUser(null, id, notification,
                            UserHandle.ALL);
                }
            } else if (mAdbNotificationShown) {
                mAdbNotificationShown = false;
                mNotificationManager.cancelAsUser(null, id, UserHandle.ALL);
            }
        }

        private String getDefaultFunctions() {
            String func = SystemProperties.get(USB_PERSISTENT_CONFIG_PROPERTY,
                    UsbManager.USB_FUNCTION_NONE);
            if (UsbManager.USB_FUNCTION_NONE.equals(func)) {
                func = UsbManager.USB_FUNCTION_MTP;
            }
            return func;
        }

        public void dump(IndentingPrintWriter pw) {
            pw.println("USB Device State:");
            pw.println("  mCurrentFunctions: " + mCurrentFunctions);
            pw.println("  mCurrentFunctionsApplied: " + mCurrentFunctionsApplied);
            pw.println("  mConnected: " + mConnected);
            pw.println("  mConfigured: " + mConfigured);
            pw.println("  mUsbDataUnlocked: " + mUsbDataUnlocked);
            pw.println("  mCurrentAccessory: " + mCurrentAccessory);
            try {
                pw.println("  Kernel state: "
                        + FileUtils.readTextFile(new File(STATE_PATH), 0, null).trim());
                pw.println("  Kernel function list: "
                        + FileUtils.readTextFile(new File(FUNCTIONS_PATH), 0, null).trim());
            } catch (IOException e) {
                pw.println("IOException: " + e);
            }
        }
    }

    /* returns the currently attached USB accessory */
    public UsbAccessory getCurrentAccessory() {
        return mHandler.getCurrentAccessory();
    }

    /* opens the currently attached USB accessory */
    public ParcelFileDescriptor openAccessory(UsbAccessory accessory) {
        UsbAccessory currentAccessory = mHandler.getCurrentAccessory();
        if (currentAccessory == null) {
            throw new IllegalArgumentException("no accessory attached");
        }
        if (!currentAccessory.equals(accessory)) {
            String error = accessory.toString()
                    + " does not match current accessory "
                    + currentAccessory;
            throw new IllegalArgumentException(error);
        }
        getCurrentSettings().checkPermission(accessory);
        return nativeOpenAccessory();
    }

    public boolean isFunctionEnabled(String function) {
        return UsbManager.containsFunction(SystemProperties.get(USB_CONFIG_PROPERTY), function);
    }

    public void setCurrentFunctions(String functions) {
        if (DEBUG) Slog.d(TAG, "setCurrentFunctions(" + functions + ")");
        if (!UsbManager.USB_FUNCTION_RNDIS.equals(functions)) { // Do not remember rndis function
            mRememberedFunctions = functions;
        }
        mHandler.sendMessage(MSG_SET_CURRENT_FUNCTIONS, functions);
    }

    public void setUsbDataUnlocked(boolean unlocked) {
        if (DEBUG) Slog.d(TAG, "setUsbDataUnlocked(" + unlocked + ")");
        mHandler.sendMessage(MSG_SET_USB_DATA_UNLOCKED, unlocked);
    }

    private void readOemUsbOverrideConfig() {
        String[] configList = mContext.getResources().getStringArray(
            com.android.internal.R.array.config_oemUsbModeOverride);

        if (configList != null) {
            for (String config: configList) {
                String[] items = config.split(":");
                if (items.length == 3) {
                    if (mOemModeMap == null) {
                        mOemModeMap = new HashMap<String, List<Pair<String, String>>>();
                    }
                    List<Pair<String, String>> overrideList = mOemModeMap.get(items[0]);
                    if (overrideList == null) {
                        overrideList = new LinkedList<Pair<String, String>>();
                        mOemModeMap.put(items[0], overrideList);
                    }
                    overrideList.add(new Pair<String, String>(items[1], items[2]));
                }
            }
        }
    }

    private String applyOemOverrideFunction(String usbFunctions) {
        if ((usbFunctions == null) || (mOemModeMap == null)) return usbFunctions;

        String bootMode = SystemProperties.get(BOOT_MODE_PROPERTY, "unknown");

        List<Pair<String, String>> overrides = mOemModeMap.get(bootMode);
        if (overrides != null) {
            for (Pair<String, String> pair: overrides) {
                if (pair.first.equals(usbFunctions)) {
                    Slog.d(TAG, "OEM USB override: " + pair.first + " ==> " + pair.second);
                    return pair.second;
                }
            }
        }
        // return passed in functions as is.
        return usbFunctions;
    }

    public void allowUsbDebugging(boolean alwaysAllow, String publicKey) {
        if (mDebuggingManager != null) {
            mDebuggingManager.allowUsbDebugging(alwaysAllow, publicKey);
        }
    }

    public void denyUsbDebugging() {
        if (mDebuggingManager != null) {
            mDebuggingManager.denyUsbDebugging();
        }
    }

    public void clearUsbDebuggingKeys() {
        if (mDebuggingManager != null) {
            mDebuggingManager.clearUsbDebuggingKeys();
        } else {
            throw new RuntimeException("Cannot clear Usb Debugging keys, "
                        + "UsbDebuggingManager not enabled");
        }
    }

    public void dump(IndentingPrintWriter pw) {
        if (mHandler != null) {
            mHandler.dump(pw);
        }
        if (mDebuggingManager != null) {
            mDebuggingManager.dump(pw);
        }
    }

    private native String[] nativeGetAccessoryStrings();
    private native ParcelFileDescriptor nativeOpenAccessory();
    private native boolean nativeIsStartRequested();
    private native int nativeGetAudioMode();
}
