/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.keyguard;

import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.widget.LockPatternUtils;
import com.sprd.keyguard.KeyguardPluginsHelper;
import android.os.SystemProperties;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.util.Log;

/**
 * This class implements a smart emergency button that updates itself based
 * on telephony state.  When the phone is idle, it is an emergency call button.
 * When there's a call in progress, it presents an appropriate message and
 * allows the user to return to the call.
 */
public class EmergencyButton extends Button {
    private static final Intent INTENT_EMERGENCY_DIAL = new Intent()
            .setAction("com.android.phone.EmergencyDialer.DIAL")
            .setPackage("com.android.phone")
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    /* Feature 478270 Add EmergencyButton on the Lockscreen{ */
    private static final String TAG = "EmergencyButtons";
    private static final boolean DEBUG = /* Debug.isDebug() */true;
    private int mNumPhones;
    private TelephonyManager mTelephonyManager;
    private SubscriptionManager  mSubscriptionManager;
    private PhoneStateListener[] mPhoneStateListeners;
    static ServiceState[] mServiceStates;
    private int[] mSubIds;
    private SubInfoUpdateReceiver mSubInfoUpdateReceiver;
    /* @} */
    KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onSimStateChanged(int subId, int slotId, State simState) {
            /* Feature 478270 Add EmergencyButton on the Lockscreen{ */
            if ( mSubscriptionManager == null ) {
                mSubscriptionManager = SubscriptionManager.from(mContext);
            }
            /* @} */
            updateEmergencyCallButton();
        }

        @Override
        public void onPhoneStateChanged(int phoneState) {
            updateEmergencyCallButton();
        }
    };

    public interface EmergencyButtonCallback {
        public void onEmergencyButtonClickedWhenInCall();
    }

    private LockPatternUtils mLockPatternUtils;
    private PowerManager mPowerManager;
    private EmergencyButtonCallback mEmergencyButtonCallback;

    private final boolean mIsVoiceCapable;
    private final boolean mEnableEmergencyCallWhileSimLocked;

    public EmergencyButton(Context context) {
        this(context, null);
    }

    public EmergencyButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        mIsVoiceCapable = context.getResources().getBoolean(
                com.android.internal.R.bool.config_voice_capable);
        mEnableEmergencyCallWhileSimLocked = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_enable_emergency_call_while_sim_locked);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mInfoCallback);
        /* Feature 478270 Add EmergencyButton on the Lockscreen{ */
        IntentFilter intentFilter = new IntentFilter(TelephonyIntents.ACTION_SUBINFO_RECORD_UPDATED);
        if (mSubInfoUpdateReceiver == null) {
            mSubInfoUpdateReceiver = new SubInfoUpdateReceiver();
        }
        mContext.registerReceiver(mSubInfoUpdateReceiver, intentFilter);
        /* @} */
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mInfoCallback);
        /* SPRD: Feature 478270 Add EmergencyButton on the Lockscreen{ */
        listenPhoneState(PhoneStateListener.LISTEN_NONE);
        for (int i = 0; i < mPhoneStateListeners.length; i++) {
            mPhoneStateListeners[i] = null;
        }
        mContext.unregisterReceiver(mSubInfoUpdateReceiver);
        mSubInfoUpdateReceiver = null;
        /* @} */
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mLockPatternUtils = new LockPatternUtils(mContext);
        mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        /* Feature 478270 Add EmergencyButton on the Lockscreen@{ */
        mTelephonyManager = TelephonyManager.from(mContext);
        mSubscriptionManager = SubscriptionManager.from(mContext);
        mNumPhones =TelephonyManager.from(mContext).getPhoneCount();
        if(mServiceStates == null){
            mServiceStates = new ServiceState[mNumPhones];
        }
        mPhoneStateListeners = new PhoneStateListener[mNumPhones];
        /* SPRD: modify by BUG 499054 @{ */
        mSubIds = new int[0];
        listenPhoneState(PhoneStateListener.LISTEN_SERVICE_STATE);
        /* @} */
        /* @} */
        setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                takeEmergencyCallAction();
            }
        });
        updateEmergencyCallButton();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateEmergencyCallButton();
    }

    /**
     * Shows the emergency dialer or returns the user to the existing call.
     */
    public void takeEmergencyCallAction() {
        MetricsLogger.action(mContext, MetricsLogger.ACTION_EMERGENCY_CALL);
        // TODO: implement a shorter timeout once new PowerManager API is ready.
        // should be the equivalent to the old userActivity(EMERGENCY_CALL_TIMEOUT)
        mPowerManager.userActivity(SystemClock.uptimeMillis(), true);
        if (isInCall()) {
            resumeCall();
            if (mEmergencyButtonCallback != null) {
                mEmergencyButtonCallback.onEmergencyButtonClickedWhenInCall();
            }
        } else {
            KeyguardUpdateMonitor.getInstance(mContext).reportEmergencyCallAction(
                    true /* bypassHandler */);
            getContext().startActivityAsUser(INTENT_EMERGENCY_DIAL,
                    ActivityOptions.makeCustomAnimation(getContext(), 0, 0).toBundle(),
                    new UserHandle(KeyguardUpdateMonitor.getCurrentUser()));
        }
    }

    private void updateEmergencyCallButton() {
        /*
        boolean visible = false;
        if (mIsVoiceCapable) {
            // Emergency calling requires voice capability.
            if (isInCall()) {
                visible = true; // always show "return to call" if phone is off-hook
            } else {
                final boolean simLocked = KeyguardUpdateMonitor.getInstance(mContext)
                        .isSimPinVoiceSecure();
                if (simLocked) {
                    // Some countries can't handle emergency calls while SIM is locked.
                    visible = mEnableEmergencyCallWhileSimLocked;
                } else {
                    // Only show if there is a secure screen (pin/pattern/SIM pin/SIM puk);
                    visible = mLockPatternUtils.isSecure(KeyguardUpdateMonitor.getCurrentUser());
                }
            }
        }*/
        if (mIsVoiceCapable) {
            /* SPRD: modify by BUG 529668 @{ */
            boolean keyShowEccButton = KeyguardPluginsHelper.getInstance().makeEmergencyVisible();
            setVisibility(keyShowEccButton ? View.VISIBLE : View.GONE);
            /* @} */
            /* @} */
            int textId;
            if (isInCall()) {
                textId = com.android.internal.R.string.lockscreen_return_to_call;
            } else {
                if (!canMakeEmergencyCall()) {
                    setClickable(false);
                    textId = R.string.kg_emergency_call_no_service;
                } else {
                    setClickable(true);
                    textId = com.android.internal.R.string.lockscreen_emergency_call;
                 }
                //textId = com.android.internal.R.string.lockscreen_emergency_call;
            }
            setText(textId);
        } else {
            setVisibility(View.GONE);
        }
    }

    public void setCallback(EmergencyButtonCallback callback) {
        mEmergencyButtonCallback = callback;
    }

    /**
     * Resumes a call in progress.
     */
    private void resumeCall() {
        getTelecommManager().showInCallScreen(false);
    }

    /**
     * @return {@code true} if there is a call currently in progress.
     */
    private boolean isInCall() {
        return getTelecommManager().isInCall();
    }

    private TelecomManager getTelecommManager() {
        return (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
    }

    /* Feature 478270 Add EmergencyButton on the Lockscreen@{ */
    private PhoneStateListener getPhoneStateListener(int subId) {
        final int phoneId = getPhoneId(subId);

        if (mPhoneStateListeners[phoneId] == null) {
            mPhoneStateListeners[phoneId] = new PhoneStateListener(subId) {
                @Override
                public void onServiceStateChanged(ServiceState state) {
                    if (state != null) {
                        if (DEBUG)
                        Log.d(TAG, "onServiceStateChanged(), serviceState = " + state + ", phoneId = " + phoneId);
                        mServiceStates[phoneId] = state;
                    }
                    updateEmergencyCallButton();
                }
            };
        }
        return mPhoneStateListeners[phoneId];
    }
    private boolean hasService(int phoneId) {
        if (mServiceStates[phoneId] != null) {
            switch (mServiceStates[phoneId].getState()) {
                case ServiceState.STATE_OUT_OF_SERVICE:
                    return false;
                case ServiceState.STATE_POWER_OFF:
                    /* SPRD: bug #541049. @{ */
                    if (Settings.Global.getInt(mContext.getContentResolver(),
                            Settings.Global.AIRPLANE_MODE_ON, 0) != 0) {
                        return true;
                    }
                    /* @} */
                    return false;
                default:
                    return true;
            }
        } else {
            return false;
        }

    }
    boolean canMakeEmergencyCall(){
        for(int i=0; i<mServiceStates.length; i++) {
            if (mServiceStates[i] != null) {
                if (mServiceStates[i].isEmergencyOnly() || hasService(i)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void listenPhoneState(int listenMode) {
        if (mSubIds == null || (mSubIds != null && mSubIds.length == 0)) {
            mTelephonyManager.listen(getInvalidSubPhoneStateListener(), listenMode);
        } else {
            for (int i = 0; i< mSubIds.length; i++) {
                mTelephonyManager.listen(getPhoneStateListener(mSubIds[i]), listenMode);
            }
        }
    }

    private int getPhoneId(int subId) {
        /* SPRD: Use telephony internal API. See bug #513463. @{ */
        int phoneId = SubscriptionManager.getPhoneId(subId);
        if (!SubscriptionManager.isValidPhoneId(phoneId)) {
            phoneId = 0;
        }
        return phoneId;
        /* @} */
    }

    class SubInfoUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "SubInfoUpdateReceiver-onReceive");
            /* SPRD: modify by BUG 499054 @{ */
            int[] subIds = mSubscriptionManager.getActiveSubscriptionIdList();
            if (mSubIds.length != subIds.length) {
                mSubIds = subIds;
                Log.d(TAG, "SubInfoUpdate, mSubIds = " + mSubIds);
                for (int i = 0; i < mPhoneStateListeners.length; i++) {
                    if (mPhoneStateListeners[i] != null) {
                        mTelephonyManager.listen(mPhoneStateListeners[i], 0);
                        mPhoneStateListeners[i] = null;
                    }
                }
            }
            /* @} */
            listenPhoneState(PhoneStateListener.LISTEN_SERVICE_STATE);
        }
    }

    private PhoneStateListener getInvalidSubPhoneStateListener () {
        if (mPhoneStateListeners[0] == null) {
            mPhoneStateListeners[0] = new PhoneStateListener() {
                @Override
                public void onServiceStateChanged(ServiceState state) {
                    super.onServiceStateChanged(state);
                    if (state != null) {
                        Log.d(TAG, "InvalidSubPhoneStateListener: onServiceStateChanged(), serviceState = " + state);
                        mServiceStates[0] = state;
                    }
                    updateEmergencyCallButton();
                }
            };
        }
        return mPhoneStateListeners[0];
    }
    /* @} */
}
