/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.internal.telephony.ITelephony;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.telephony.PhoneConstants;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ImageView;
import android.view.View;

/**
 * Displays a PIN pad for unlocking.
 */
public class KeyguardSimPinView extends KeyguardPinBasedInputView {
    private static final String LOG_TAG = "KeyguardSimPinView";
    private static final boolean DEBUG = KeyguardConstants.DEBUG_SIM_STATES;
    public static final String TAG = "KeyguardSimPinView";

    private ProgressDialog mSimUnlockProgressDialog = null;
    private CheckSimPin mCheckSimPinThread;

    private AlertDialog mRemainingAttemptsDialog;
    private int mSubId;
    private ImageView mSimImageView;

    KeyguardUpdateMonitorCallback mUpdateMonitorCallback = new KeyguardUpdateMonitorCallback() {
        @Override
        public void onSimStateChanged(int subId, int slotId, State simState) {
           if (DEBUG) Log.v(TAG, "onSimStateChanged(subId=" + subId + ",state=" + simState + ")");
           resetState();
       };
    };

    // SPRD :Show pin/puk remaining times.
    private int mRemainTimes;

    public KeyguardSimPinView(Context context) {
        this(context, null);
    }

    public KeyguardSimPinView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void resetState() {
        super.resetState();
        if (DEBUG) Log.v(TAG, "Resetting state");
        KeyguardUpdateMonitor monitor = KeyguardUpdateMonitor.getInstance(mContext);
        mSubId = monitor.getNextSubIdForState(IccCardConstants.State.PIN_REQUIRED);
        if (SubscriptionManager.isValidSubscriptionId(mSubId)) {
            int count = TelephonyManager.getDefault().getSimCount();
            Resources rez = getResources();
            final String msg;
            int color = Color.WHITE;
            if (count < 2) {
                msg = rez.getString(R.string.kg_sim_pin_instructions);
            } else {
                SubscriptionInfo info = monitor.getSubscriptionInfoForSubId(mSubId);
                /* SPRD :Show pin/puk remaining times. @{*/
                CharSequence displayName = info != null ? "SIM"+Integer.toString(SubscriptionManager.getPhoneId(mSubId)+1) : ""; // don't crash
                // SPRD: It need subId here, See bug #494466.
                mRemainTimes = monitor.getRemainstimes(TelephonyManager.UNLOCK_PIN, mSubId);
                msg = rez.getString(R.string.kg_sim_pin_instructions_multi, displayName, mRemainTimes);
                /* @} */
                if (info != null) {
                    color = info.getIconTint();
                }
            }
            mSecurityMessageDisplay.setMessage(msg, true);
            mSimImageView.setImageTintList(ColorStateList.valueOf(color));
        }
    }

    @Override
    protected int getPromtReasonStringRes(int reason) {
        // No message on SIM Pin
        return 0;
    }

    private String getPinPasswordErrorMessage(int attemptsRemaining) {
        String displayMessage;

        if (attemptsRemaining == 0) {
            displayMessage = getContext().getString(R.string.kg_password_wrong_pin_code_pukked);
        } else if (attemptsRemaining > 0) {
            displayMessage = getContext().getResources()
                    .getQuantityString(R.plurals.kg_password_wrong_pin_code, attemptsRemaining,
                            attemptsRemaining);
        } else {
            displayMessage = getContext().getString(R.string.kg_password_pin_failed);
        }
        if (DEBUG) Log.d(LOG_TAG, "getPinPasswordErrorMessage:"
                + " attemptsRemaining=" + attemptsRemaining + " displayMessage=" + displayMessage);
        return displayMessage;
    }

    @Override
    protected boolean shouldLockout(long deadline) {
        // SIM PIN doesn't have a timed lockout
        return false;
    }

    @Override
    protected int getPasswordTextViewId() {
        return R.id.simPinEntry;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mSecurityMessageDisplay.setTimeout(0); // don't show ownerinfo/charging status by default
        if (mEcaView instanceof EmergencyCarrierArea) {
            ((EmergencyCarrierArea) mEcaView).setCarrierTextVisible(true);
        }
        mSimImageView = (ImageView) findViewById(R.id.keyguard_sim);
        // SPRD:add for bug 597244 for limit 8 numbers
        mPasswordEntry.setLimit(true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mUpdateMonitorCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mUpdateMonitorCallback);
    }

    @Override
    public void showUsabilityHint() {
    }

    @Override
    public void onPause() {
        // dismiss the dialog.
        /* SPRD:add PIN verify success prompt @{ */
        postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "onPause-postDelayed : mSimUnlockProgressDialog ="
                        + mSimUnlockProgressDialog);
                if (mSimUnlockProgressDialog != null) {
                    mSimUnlockProgressDialog.dismiss();
                    mSimUnlockProgressDialog = null;
                    Log.d(TAG, "onPause(); "
                            + "prompt subscribers PIN unlock success for one second");
                }
            }
        }, 500);
        /* @} */
    }

    /**
     * Since the IPC can block, we want to run the request in a separate thread
     * with a callback.
     */
    private abstract class CheckSimPin extends Thread {
        private final String mPin;
        private int mSubId;

        protected CheckSimPin(String pin, int subId) {
            mPin = pin;
            mSubId = subId;
        }

        abstract void onSimCheckResponse(final int result, final int attemptsRemaining);

        @Override
        public void run() {
            try {
                if (DEBUG) {
                    Log.v(TAG, "call supplyPinReportResultForSubscriber(subid=" + mSubId + ")");
                }
                final int[] result = ITelephony.Stub.asInterface(ServiceManager
                        .checkService("phone")).supplyPinReportResultForSubscriber(mSubId, mPin);
                if (DEBUG) {
                    Log.v(TAG, "supplyPinReportResult returned: " + result[0] + " " + result[1]);
                }
                post(new Runnable() {
                    public void run() {
                        onSimCheckResponse(result[0], result[1]);
                    }
                });
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException for supplyPinReportResult:", e);
                post(new Runnable() {
                    public void run() {
                        onSimCheckResponse(PhoneConstants.PIN_GENERAL_FAILURE, -1);
                    }
                });
            }
        }
    }

    private Dialog getSimUnlockProgressDialog() {
        if (mSimUnlockProgressDialog == null) {
            mSimUnlockProgressDialog = new ProgressDialog(mContext);
            mSimUnlockProgressDialog.setMessage(
                    mContext.getString(R.string.kg_sim_unlock_progress_dialog_message));
            mSimUnlockProgressDialog.setIndeterminate(true);
            mSimUnlockProgressDialog.setCancelable(false);
            mSimUnlockProgressDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        }
        return mSimUnlockProgressDialog;
    }

    private Dialog getSimRemainingAttemptsDialog(int remaining) {
        String msg = getPinPasswordErrorMessage(remaining);
        if (mRemainingAttemptsDialog == null) {
            Builder builder = new AlertDialog.Builder(mContext);
            builder.setMessage(msg);
            builder.setCancelable(false);
            builder.setNeutralButton(R.string.ok, null);
            mRemainingAttemptsDialog = builder.create();
            mRemainingAttemptsDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        } else {
            mRemainingAttemptsDialog.setMessage(msg);
        }
        return mRemainingAttemptsDialog;
    }

    @Override
    protected void verifyPasswordAndUnlock() {
        String entry = mPasswordEntry.getText();

        //SPRD: modify by BUG 507939
        if (entry.length() < 4 || entry.length() > 8) {
            // otherwise, display a message to the user, and don't submit.
            mSecurityMessageDisplay.setMessage(R.string.kg_invalid_sim_pin_hint, true);
            resetPasswordText(true);
            mCallback.userActivity();
            return;
        }

        getSimUnlockProgressDialog().show();

        if (mCheckSimPinThread == null) {
            mCheckSimPinThread = new CheckSimPin(mPasswordEntry.getText(), mSubId) {
                void onSimCheckResponse(final int result, final int attemptsRemaining) {
                    post(new Runnable() {
                        public void run() {
                            resetPasswordText(true /* animate */);
                            if (result == PhoneConstants.PIN_RESULT_SUCCESS) {
                                /* SPRD:add PIN verify success prompt @{ */
                                if (mSimUnlockProgressDialog != null) {
                                    mSimUnlockProgressDialog.setMessage(
                                            mContext.getString(R.string.kg_sim_pin_verify_success));
                                }
                                /* @} */
                                KeyguardUpdateMonitor.getInstance(getContext())
                                        .reportSimUnlocked(mSubId);
                                mCallback.dismiss(true);
                                /* SPRD: 616620 @{ */
                                //setVisibility(View.GONE);
                                /* @} */
                                /* SPRD: 608681 @{ */
                                /* SPRD: 601302 hide after verfiy successfully @{ */
                                View parentView = (KeyguardSecurityContainer)KeyguardSimPinView.this.getParent().getParent();
                                if(parentView != null && parentView instanceof KeyguardSecurityContainer){
                                    KeyguardSecurityContainer keyguardSecurityContainer = (KeyguardSecurityContainer)parentView;
                                    if(keyguardSecurityContainer.getSecurityMode() != KeyguardSecurityModel.SecurityMode.SimPin){
                                         KeyguardSimPinView.this.post(new Runnable() {
                                             @Override
                                             public void run() {
                                                 /* SPRD: 617561 @{ */
                                                 setVisibility(View.GONE);
                                                 /* @} */
                                             }
                                         });
                                    }
                                }
                                /* @} */
                                /* @} */
                            } else {
                                if (mSimUnlockProgressDialog != null) {
                                    mSimUnlockProgressDialog.hide();
                                }
                                if (result == PhoneConstants.PIN_PASSWORD_INCORRECT) {
                                    /* SPRD: add cancel for sim pin and puk @{ */
                                    mRemainTimes = attemptsRemaining;
                                    resetState();
                                    /* @} */
                                    if (attemptsRemaining <= 2) {
                                        // this is getting critical - show dialog
                                        getSimRemainingAttemptsDialog(attemptsRemaining).show();
                                    } else {
                                        // show message
                                        mSecurityMessageDisplay.setMessage(
                                                getPinPasswordErrorMessage(attemptsRemaining), true);
                                    }
                                } else {
                                    // "PIN operation failed!" - no idea what this was and no way to
                                    // find out. :/
                                    mSecurityMessageDisplay.setMessage(getContext().getString(
                                            R.string.kg_password_pin_failed), true);
                                }
                                if (DEBUG) Log.d(LOG_TAG, "verifyPasswordAndUnlock "
                                        + " CheckSimPin.onSimCheckResponse: " + result
                                        + " attemptsRemaining=" + attemptsRemaining);
                            }
                            mCallback.userActivity();
                            mCheckSimPinThread = null;
                        }
                    });
                }
            };
            mCheckSimPinThread.start();
        }
    }

    @Override
    public void startAppearAnimation() {
        // noop.
    }

    @Override
    public boolean startDisappearAnimation(Runnable finishRunnable) {
        return false;
    }
}

