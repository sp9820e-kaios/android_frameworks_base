package com.sprd.keyguard;

import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.telephony.ServiceState;
import android.telephony.SubscriptionInfo;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.internal.telephony.TeleUtils;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.IccCardConstants.State;
import com.android.internal.widget.LockPatternUtils;
import com.sprd.keyguard.KeyguardConfig;
import com.android.keyguard.KeyguardUpdateMonitorCallback;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.keyguard.R;
import java.util.Map;
import java.util.HashMap;
import android.os.SystemProperties;//sprd: bug615857

public class CarriersTextLayout extends LinearLayout {
    private State[] mSimStates;
    private TextView[] mCarrierViews;
    private static final boolean DEBUG = true;
    private static final String TAG = "CarriersTextLayout";
    private KeyguardUpdateMonitor mKeyguardUpdateMonitor;
    private LockPatternUtils mLockPatternUtils;
    private static CharSequence mSeparator;
    private Context mContext;
    private String[] mCarrierName;
    private final boolean mIsEmergencyCallCapable;
    /* SPRD: modify for bug 494056 @{ */
    private String mOperatorSeparatorLabel;
    /* @} */
    // SPRD: Bug 532196 modify for flight mode name display in reliance.
    private boolean isShowFlightMode = KeyguardPluginsHelper.getInstance()
            .showFlightMode();

    /* SPRD: modify by BUG 540847 @{ */
    private Map<Integer, Boolean> mShowPlmn = new HashMap<Integer, Boolean>();
    private Map<Integer, Boolean> mShowSpn = new HashMap<Integer, Boolean>();
    private Map<Integer, String> mPlmn = new HashMap<Integer, String>();
    private Map<Integer, String> mSpn = new HashMap<Integer, String>();
    private int mNumPhones;
    private ServiceState[] mServiceStates;
    private KeyguardConfig mKeyguardConfig;
    /* @} */
    public static boolean IS_QQVGA_BOARD = SystemProperties.getBoolean("ro.board.qqvga",false);//sprd: bug615857
    private KeyguardUpdateMonitorCallback mCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onRefreshCarrierInfo() {
            updateCarrierText();
        }

        @Override
        public void onSimStateChanged(int subId, int slotId, IccCardConstants.State simState) {
            /* SPRD: add for bug 503807 to refresh sim card's oprator name @{ */
            if (DEBUG) {
                Log.d(TAG, "onSimStateChanged implements: simState = "
                        + simState + ", subId=" + subId + ";slotId =" + slotId);
            }
            if (SubscriptionManager.isValidSlotId(slotId)) {
                mSimStates[slotId] = simState;
                updateCarrierText();
            }
            /* @} */
        }

        /* SPRD: modify by BUG 540847 @{ */
        @Override
        public void onServiceStateChange(int subId, ServiceState state) {
            if (DEBUG) {
                Log.d(TAG, "onServiceStateChanged ServiceState : " + state + "; subId : "
                    + subId);
            }
            int phoneId = SubscriptionManager.getSlotId(subId);
            if (phoneId >= 0 && phoneId < mServiceStates.length) {
                mServiceStates[phoneId] = state;
            }
            TelephonyManager tm = TelephonyManager.getDefault();
            // SPRD: modify by BUG 554166
            if (state != null && SubscriptionManager.isValidPhoneId(phoneId)) {
                updateNetworkName(mShowSpn.get(phoneId), mSpn.get(phoneId),
                        mShowPlmn.get(phoneId), mPlmn.get(phoneId), phoneId);
            }
        }
        /* @} */
    };
    /**
     * The status of this lock screen. Primarily used for widgets on LockScreen.
     */
    public CarriersTextLayout(Context context) {
        this(context, null);
    }

    public CarriersTextLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mIsEmergencyCallCapable = context.getResources().getBoolean(
                com.android.internal.R.bool.config_voice_capable);
        mLockPatternUtils = new LockPatternUtils(context);
        mContext = context;
        /* SPRD: modify by BUG 540847 @{ */
        mKeyguardConfig = KeyguardConfig.getInstance(context);
        mNumPhones = TelephonyManager.getDefault().getPhoneCount();
        if (mServiceStates == null) {
            mServiceStates = new ServiceState[mNumPhones];
        }
        /* @} */
        /* SPRD: modify by BUG 540847 @{ */
        int phoneCount = TelephonyManager.from(mContext).getPhoneCount();
        for (int i = 0; i < phoneCount; i++) {
            mShowPlmn.put(i, false);
            mShowSpn.put(i, false);
        }
        /* @} */
    }

    protected void updateCarrierText() {
        /* SPRD: modify for bug 503807 to refresh sim card's oprator name @{ */
        List<SubscriptionInfo> subs = mKeyguardUpdateMonitor.getSubscriptionInfo(false);
        final int subCount = subs.size();
        if (DEBUG) Log.d(TAG, "updateCarrierText(): " + subCount);
        boolean isAllCardsAbsent = true;
        int phoneCount = TelephonyManager.from(mContext).getPhoneCount();
        // SPRD: Bug 532196 modify for flight mode name display in reliance.
        TelephonyManager mTelephonyManager = TelephonyManager.from(mContext);
        SpannableStringBuilder[] carrierNames = new SpannableStringBuilder[phoneCount];
        for (int i = 0; i < phoneCount; i++) {
            isAllCardsAbsent = isAllCardsAbsent
                    && (mSimStates[i] == IccCardConstants.State.ABSENT);
        }
        Log.d(TAG, "isAllCardsAbsent = " + isAllCardsAbsent);
        Log.d(TAG, "isShowFlightMode = " + isShowFlightMode);
        /* SPRD: Bug 532196 modify for flight mode name display in reliance @{ */
        if (isShowFlightMode && mTelephonyManager.isAirplaneModeOn()) {
            Log.d(TAG, "isAirplaneModeOn = " + mTelephonyManager.isAirplaneModeOn());
            carrierNames[0] = new SpannableStringBuilder(getContext().getText(
                    R.string.keyguard_flight_mode));
            carrierNames[0].setSpan(new ForegroundColorSpan(0xFFFFFFFF), 0,
                    carrierNames[0].length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (isAllCardsAbsent || subCount == 0) {
            carrierNames[0] = new SpannableStringBuilder(getContext().getText(R.string.kg_no_sim_message));
            carrierNames[0].setSpan(new ForegroundColorSpan(0xFFFFFFFF), 0,
                    carrierNames[0].length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            /* SPRD: modify for 513066 avoid ArrayIndexOutOfBoundsException @{ */
            int realCount = (subCount > phoneCount ? phoneCount : subCount);
            for (int i = 0; i < realCount; i++) {
            /* @} */
                SubscriptionInfo subInfo  = subs.get(i);
                CharSequence carrierName = subInfo.getCarrierName();
                /* SPRD: modify for bug 434366 @{ */
                int phoneId = subInfo.getSimSlotIndex();
                /* @} */
                if (DEBUG) Log.d(TAG, "Handling carrierName = " + carrierName);
                int color = 0xFFFFFFFF;
                /* SPRD: modify by BUG 494698 @{ */
                boolean simTitlebarColorVariableBool = mKeyguardConfig.shouldShowColorfulSystemUI();
                Log.d(TAG, "simTitlebarColorVariableBool  = " + simTitlebarColorVariableBool);
                if (subInfo != null && simTitlebarColorVariableBool) {
                    color = subInfo.getIconTint();
                }
                /* @} */

                /* SPRD: modify by BUG 529444 @{ */
                if (phoneId >= mCarrierName.length) {
                    return;
                }
                /* @} */
                if (mCarrierName != null && !TextUtils.isEmpty(mCarrierName[phoneId])) {
                    // SPRD: modify for bug 434366
                    Log.d(TAG, "mCarrierName" + "[" + phoneId + "] :" + mCarrierName[phoneId]);
                    // SPRD: modify for bug 511349
                    int id = SimlockStatusHelper.getInstance().getSimLockStringId(phoneId);
                    String tmpCarrierName = mCarrierName[phoneId];
                    if(id > 0) {
                       if (tmpCarrierName.isEmpty()) {
                           tmpCarrierName = getResources().getString(id);
                       } else {
                           tmpCarrierName = tmpCarrierName + "|" + getResources().getString(id);
                       }
                    }
                    Log.d(TAG, "tmpCarrierName :" + tmpCarrierName);
                   carrierNames[i] = new SpannableStringBuilder(tmpCarrierName);
                } else {
                    Log.d(TAG, "carrierName :" + carrierName);
                    carrierNames[i] = new SpannableStringBuilder(carrierName);
                }
                carrierNames[i].setSpan(new ForegroundColorSpan(color), 0,
                        carrierNames[i].length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        /* @} */
        /* SPRD: modify for 513066 avoid ArrayIndexOutOfBoundsException @{ */
        int viewsLength = mCarrierViews.length;
        int count = (phoneCount > viewsLength ? viewsLength : phoneCount);
        for (int i = 0; i < count; i++) {
        /* @} */
            mCarrierViews[i].setText(carrierNames[i]);
            mCarrierViews[i].setVisibility(TextUtils.isEmpty(carrierNames[i]) ? View.GONE : View.VISIBLE);
        }
        /* @} */
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateCarrierText();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSeparator = getResources().getString(
                com.android.internal.R.string.kg_text_message_separator);
        int phoneCount = TelephonyManager.from(mContext).getPhoneCount();
        mSimStates = new State[phoneCount];
        mCarrierViews = new TextView[phoneCount];
        mCarrierName = new String[phoneCount];
        for (int i = 0; i < phoneCount; i++) {
            TextView carrier = new TextView(getContext());
            carrier.setGravity(Gravity.CENTER_HORIZONTAL);
            carrier.setSingleLine();
            /* SPRD: Bug 609454 @{ */
            /* SPRD: Bug 615857 @{ */
            Log.v(TAG, "CarriersTextLayout IS_QQVGA_BOARD = " + IS_QQVGA_BOARD);
            if(IS_QQVGA_BOARD)
            {
                carrier.setTextSize(TypedValue.COMPLEX_UNIT_PX, 12);
            }else{
                carrier.setTextSize(TypedValue.COMPLEX_UNIT_PX, 25);
            }
            /* @{ */
            /* @{ */
            carrier.setTextAppearance(mContext, android.R.attr.textAppearanceMedium);
            addView(carrier);
            mCarrierViews[i] = carrier;
        }
        setSelected(true); // Allow marquee to work.
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mKeyguardUpdateMonitor = KeyguardUpdateMonitor.getInstance(mContext);
        mKeyguardUpdateMonitor.registerCallback(mCallback);
        registerListeners();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mCallback);
        mContext.unregisterReceiver(mBroadcastReceiver);
        /* SPRD: modify by BUG 540847 @{ */
        mKeyguardConfig.dispose();
        /* @} */
    }

    /*
     * Add emergencyCallMessage to carrier string only if phone supports emergency calls.
     */
    private CharSequence makeCarrierStringOnEmergencyCapable(
            CharSequence simMessage, CharSequence emergencyCallMessage) {
        if (mIsEmergencyCallCapable) {
            return concatenate(simMessage, emergencyCallMessage);
        }
        return simMessage;
    }

    private static CharSequence concatenate(CharSequence plmn, CharSequence spn) {
        final boolean plmnValid = !TextUtils.isEmpty(plmn);
        final boolean spnValid = !TextUtils.isEmpty(spn);
        if (plmnValid && spnValid) {
            if (plmn.equals(spn)) {
                return plmn;
            } else {
                return new StringBuilder().append(plmn).append(mSeparator).append(spn).toString();
            }
        } else if (plmnValid) {
            return plmn;
        } else if (spnValid) {
            return spn;
        } else {
            return "";
        }
    }

    private void registerListeners() {
        // broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION);
        // SPRD: modify for bug 503807 to refresh sim card's oprator name
        //filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        // SPRD: Bug 532196 modify for flight mode name display in reliance.
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ( action.equals(TelephonyIntents.SPN_STRINGS_UPDATED_ACTION)) {
                updateNetworkName(intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_SPN, false),
                        intent.getStringExtra(TelephonyIntents.EXTRA_SPN),
                        intent.getBooleanExtra(TelephonyIntents.EXTRA_SHOW_PLMN, false),
                        intent.getStringExtra(TelephonyIntents.EXTRA_PLMN),
                        intent.getIntExtra(PhoneConstants.PHONE_KEY,
                                SubscriptionManager.DEFAULT_PHONE_INDEX));
            /* SPRD: Bug 532196 modify for flight mode name display in reliance @{ */
            } else if (isShowFlightMode && action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                Log.d(TAG, "ACTION_AIRPLANE_MODE_CHANGED");
                updateCarrierText();
            }
            /* @} */
            /* SPRD: add for bug 503807 to refresh sim card's oprator name @{ */
            /* else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                int phoneId = intent.getIntExtra(PhoneConstants.PHONE_KEY,
                        SubscriptionManager.DEFAULT_PHONE_INDEX);
                String simState = intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE);
                if (DEBUG)
                    Log.d(TAG, "receive SIM_STATE_CHANGED [" + phoneId + "] : " + simState);
                // SPRD: Ignore sim states NOT_READY and UNKNOWN, do not to update carrier text.
                if (!IccCardConstants.INTENT_VALUE_ICC_NOT_READY.equals(simState)
                        && !IccCardConstants.INTENT_VALUE_ICC_UNKNOWN.equals(simState)) {
                    mSimStates[phoneId] = simState;
                    updateCarrierText();
                }
            }*/
            /* @} */
        }
    };

    private void updateNetworkName(boolean showSpn, String spn, boolean showPlmn, String plmn,
            int phoneid) {
        if (DEBUG) {
            Log.d(TAG, "updateNetworkName showSpn=" + showSpn + " spn=" + spn
                    + " showPlmn=" + showPlmn + " plmn=" + plmn + " PhoneId=" + phoneid);
        }
        /* SPRD: modify by BUG 545007 @{ */
        if (!SubscriptionManager.isValidPhoneId(phoneid)) {
            return;
        }
        /* @} */
        /* SPRD: add for BUG 540847 @{ */
        mShowPlmn.put(phoneid, showPlmn);
        mShowSpn.put(phoneid, showSpn);
        mPlmn.put(phoneid, plmn);
        mSpn.put(phoneid, spn);
        /* @} */
        /* SPRD: modify for bug 494056 @{ */
        if (mOperatorSeparatorLabel == null) {
            mOperatorSeparatorLabel = mKeyguardConfig.getOperatorSeparatorLabel();
        }
        /* @} */
        StringBuilder str = new StringBuilder();
        if (showPlmn && plmn != null) {
            /* SPRD: modify by BUG 540847 @{ */
            plmn = KeyguardPluginsHelper.getInstance()
                    .appendRatToNetworkName(mContext, mServiceStates[phoneid], plmn);
            /* @} */
            str.append(plmn);
        }
        if (showSpn && spn != null) {
            /* SPRD: modify by BUG 540847 @{ */
            spn = KeyguardPluginsHelper.getInstance()
                    .appendRatToNetworkName(mContext, mServiceStates[phoneid], spn);
            /* @} */
            /* SPRD: modify for bug 494056 @{ */
            if (showPlmn && plmn != null && mOperatorSeparatorLabel != null) {
                str.append(mOperatorSeparatorLabel);
            }
            /* @} */
            str.append(spn);
        }
        /* SPRD: modify by BUG 529444 @{ */
        if (phoneid < mCarrierName.length) {
            mCarrierName[phoneid] = str.toString();
        }
        /* @} */
        /* SPRD: Bug#534387 modify for network name display in reliance. @{ */
        String networkName = KeyguardPluginsHelper.getInstance().updateNetworkName(
                mContext, showSpn, spn, showPlmn, plmn);
        if (!TextUtils.isEmpty(networkName) && phoneid < mCarrierName.length) {
            mCarrierName[phoneid] = networkName;
        }
        /* @} */
        // SPRD: modify by BUG 557211
        updateCarrierText();
    }
}
