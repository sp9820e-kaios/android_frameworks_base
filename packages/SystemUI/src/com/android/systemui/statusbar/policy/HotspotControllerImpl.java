/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.os.SystemProperties;
import android.util.Log;
import android.widget.Toast;
import android.os.SystemProperties;
import android.provider.Settings;
import com.android.settingslib.TetherUtil;
import android.provider.Settings;
import android.content.ContentResolver;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

import com.android.systemui.statusbar.phone.PhoneStatusBar;

public class HotspotControllerImpl implements HotspotController {

    private static final String TAG = "HotspotController";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final Intent TETHER_SERVICE_INTENT = new Intent()
            .putExtra(TetherUtil.EXTRA_ADD_TETHER_TYPE, TetherUtil.TETHERING_WIFI)
            .putExtra(TetherUtil.EXTRA_SET_ALARM, true)
            .putExtra(TetherUtil.EXTRA_RUN_PROVISION, true)
            .putExtra(TetherUtil.EXTRA_ENABLE_WIFI_TETHER, true)
            .setComponent(TetherUtil.TETHER_SERVICE);

    private static final int MSG_SOFTAP_BT_COEXIST = 1;
    private static final int MSG_MOBILE_DATA_NEEDED = 2;

    private final ArrayList<Callback> mCallbacks = new ArrayList<Callback>();
    private final Receiver mReceiver = new Receiver();
    private final Context mContext;
    private final ConnectivityManager mConnectivityManager;
    private boolean supportBtWifiSoftApCoexist = true;
    private BluetoothAdapter mBluetoothAdapter;
    private PhoneStatusBar mBar;
    private UIHandler mHandler;

    private int mHotspotState;

    public HotspotControllerImpl(Context context) {
        mContext = context;
        mConnectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (SystemProperties.get("ro.btwifisoftap.coexist", "true").equals(
                "false")) {
            if (mBluetoothAdapter == null) {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            }
            supportBtWifiSoftApCoexist = false;
        }
        mHandler = new UIHandler();
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("HotspotController state:");
        pw.print("  mHotspotEnabled="); pw.println(stateToString(mHotspotState));
    }

    private static String stateToString(int hotspotState) {
        switch (hotspotState) {
            case WifiManager.WIFI_AP_STATE_DISABLED:
                return "DISABLED";
            case WifiManager.WIFI_AP_STATE_DISABLING:
                return "DISABLING";
            case WifiManager.WIFI_AP_STATE_ENABLED:
                return "ENABLED";
            case WifiManager.WIFI_AP_STATE_ENABLING:
                return "ENABLING";
            case WifiManager.WIFI_AP_STATE_FAILED:
                return "FAILED";
        }
        return null;
    }

    public void addCallback(Callback callback) {
        if (callback == null || mCallbacks.contains(callback)) return;
        if (DEBUG) Log.d(TAG, "addCallback " + callback);
        mCallbacks.add(callback);
        mReceiver.setListening(!mCallbacks.isEmpty());
    }

    public void removeCallback(Callback callback) {
        if (callback == null) return;
        if (DEBUG) Log.d(TAG, "removeCallback " + callback);
        mCallbacks.remove(callback);
        mReceiver.setListening(!mCallbacks.isEmpty());
    }

    @Override
    public boolean isHotspotEnabled() {
        return mHotspotState == WifiManager.WIFI_AP_STATE_ENABLED;
    }

    @Override
    public boolean isHotspotSupported() {
        return TetherUtil.isTetheringSupported(mContext);
    }

    @Override
    public void setHotspotEnabled(boolean enabled) {
        final ContentResolver cr = mContext.getContentResolver();
        // Call provisioning app which is called when enabling Tethering from
        // Settings
        if (enabled && TetherUtil.isProvisioningNeeded(mContext)) {
            mContext.startServiceAsUser(TETHER_SERVICE_INTENT,
                    UserHandle.CURRENT);
        } else {
            if (!supportBtWifiSoftApCoexist) {
                int btState = mBluetoothAdapter.getState();
                if (mBar != null
                        && enabled
                        && (btState != BluetoothAdapter.STATE_OFF)) {
                    // SPRD:fixbug434711 make the statusbar close when click
                    // hotspot.
                    mHandler.sendEmptyMessage(MSG_SOFTAP_BT_COEXIST);
                    return;
                }
                if (enabled) {
                    Settings.Global.putInt(cr,
                        Settings.Global.SOFTAP_REENABLING, 1);
                }
            }
            TetherUtil.setWifiTethering(enabled, mContext);

            /*
             * SPRD: Modify Bug 451875 show tip for wifi hotspot by mobile data
             * disabled @{
             */
            if (mBar != null && enabled
                    && !mConnectivityManager.getMobileDataEnabled()) {
                mHandler.sendEmptyMessage(MSG_MOBILE_DATA_NEEDED);
            }
            /* @} */
        }
    }

    private void fireCallback(boolean isEnabled) {
        for (Callback callback : mCallbacks) {
            callback.onHotspotChanged(isEnabled);
        }
    }

    private final class Receiver extends BroadcastReceiver {
        private boolean mRegistered;

        public void setListening(boolean listening) {
            if (listening && !mRegistered) {
                if (DEBUG) Log.d(TAG, "Registering receiver");
                final IntentFilter filter = new IntentFilter();
                filter.addAction(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
                mContext.registerReceiver(this, filter);
                mRegistered = true;
            } else if (!listening && mRegistered) {
                if (DEBUG) Log.d(TAG, "Unregistering receiver");
                mContext.unregisterReceiver(this);
                mRegistered = false;
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.d(TAG, "onReceive " + intent.getAction());
            int state = intent.getIntExtra(
                    WifiManager.EXTRA_WIFI_AP_STATE, WifiManager.WIFI_AP_STATE_FAILED);
            mHotspotState = state;
            fireCallback(mHotspotState == WifiManager.WIFI_AP_STATE_ENABLED);
        }
    }

    public void setbar(PhoneStatusBar bar) {
        // TODO Auto-generated method stub
        mBar = bar;
    }

    /*
     * SPRD: Modify Bug 451875 show tip for wifi hotspot by mobile data disabled
     *
     * @{
     */
    private void showAlertForMobileDataNeedEnabled() {
        Toast.makeText(mContext,
                com.android.systemui.R.string.softap_need_mobile_data_enabled,
                Toast.LENGTH_LONG).show();
    }
    /* @} */

    class UIHandler extends Handler {

        public UIHandler() {
            super();
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_SOFTAP_BT_COEXIST:
                mBar.animateCollapseQuickSettings();
                Toast.makeText(mContext,
                        com.android.systemui.R.string.softap_bt_cannot_coexist,
                        Toast.LENGTH_SHORT).show();
                break;
            case MSG_MOBILE_DATA_NEEDED:
                mBar.animateCollapseQuickSettings();
                showAlertForMobileDataNeedEnabled();
                break;
            }

        }
    }

}
