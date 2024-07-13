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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.ims.ImsManager;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.policy.NetworkController.IconState;
import com.android.systemui.statusbar.policy.NetworkControllerImpl;
import com.android.systemui.statusbar.policy.SecurityController;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.tuner.TunerService.Tunable;
import com.android.systemui.statusbar.policy.SystemUIPluginsHelper;
import com.android.systemui.SystemUiConfig;
import java.util.ArrayList;
import java.util.List;

// Intimately tied to the design of res/layout/signal_cluster_view.xml
public class SignalClusterView
        extends LinearLayout
        implements NetworkControllerImpl.SignalCallback,
        SecurityController.SecurityControllerCallback, Tunable {

    static final String TAG = "SignalClusterView";
    static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final String SLOT_AIRPLANE = "airplane";
    private static final String SLOT_MOBILE = "mobile";
    private static final String SLOT_WIFI = "wifi";
    private static final String SLOT_ETHERNET = "ethernet";

    NetworkControllerImpl mNC;
    SecurityController mSC;

    private boolean mNoSimsVisible = false;
    private boolean mVpnVisible = false;
    private boolean mEthernetVisible = false;
    private int mEthernetIconId = 0;
    private int mLastEthernetIconId = -1;
    private boolean mWifiVisible = false;
    private int mWifiStrengthId = 0;
    private int mLastWifiStrengthId = -1;
    /* SPRD: Add VoLte icon for bug 509601. @{ */
    private final boolean mIsSupportVolte = ImsManager.isVolteEnabledByPlatform(mContext);
    private boolean mVolteVisible = false;
    private int mVolteIconId = 0;
    private int mLastVolteIconId = -1;
    private int mImsRegisterPhoneId = -1;
    /* @} */
    /* SPRD: Add HD audio icon in cucc for bug 536924. @{ */
    private Drawable mHdVoiceDraw;
    private boolean mHdVoiceVisible = false;
    /* @} */
    // SPRD: Reliance UI spec 1.7. See bug #522899.
    private boolean isRelianceBoard = SystemUIPluginsHelper.getInstance().isReliance();
    private boolean mIsAirplaneMode = false;
    private int mAirplaneIconId = 0;
    private int mLastAirplaneIconId = -1;
    private String mAirplaneContentDescription;
    private String mWifiDescription;
    private String mEthernetDescription;

    private ArrayList<PhoneState> mPhoneStates = new ArrayList<PhoneState>();
    private int mIconTint = Color.WHITE;
    private float mDarkIntensity;

    ViewGroup mEthernetGroup, mWifiGroup;
    View mNoSimsCombo;
    // SPRD: Add VoLte icon for bug 509601.
    // SPRD: Add HD audio icon in cucc for bug 536924.
    ImageView mVpn, mEthernet, mWifi, mVolte, mHdVoice, mAirplane, mNoSims, mEthernetDark,
            mWifiDark, mNoSimsDark;
    // SPRD: add for bug 523383
    View mEthernetSpacer;
    View mWifiAirplaneSpacer;
    View mWifiSignalSpacer;
    LinearLayout mMobileSignalGroup;

    private int mWideTypeIconStartPadding;
    private int mSecondaryTelephonyPadding;
    private int mEndPadding;
    private int mEndPaddingNothingVisible;

    private boolean mBlockAirplane;
    private boolean mBlockMobile;
    private boolean mBlockWifi;
    private boolean mBlockEthernet;

    ImageView mWifiOut, mWifiIn;
    private boolean isWifiIn, isWifiOut;

    public SignalClusterView(Context context) {
        this(context, null);
    }

    public SignalClusterView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SignalClusterView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        if (!StatusBarIconController.ICON_BLACKLIST.equals(key)) {
            return;
        }
        ArraySet<String> blockList = StatusBarIconController.getIconBlacklist(newValue);
        boolean blockAirplane = blockList.contains(SLOT_AIRPLANE);
        boolean blockMobile = blockList.contains(SLOT_MOBILE);
        boolean blockWifi = blockList.contains(SLOT_WIFI);
        boolean blockEthernet = blockList.contains(SLOT_ETHERNET);

        if (blockAirplane != mBlockAirplane || blockMobile != mBlockMobile
                || blockEthernet != mBlockEthernet || blockWifi != mBlockWifi) {
            mBlockAirplane = blockAirplane;
            mBlockMobile = blockMobile;
            mBlockEthernet = blockEthernet;
            mBlockWifi = blockWifi;
            // Re-register to get new callbacks.
            mNC.removeSignalCallback(this);
            mNC.addSignalCallback(this);
        }
    }

    public void setNetworkController(NetworkControllerImpl nc) {
        if (DEBUG) Log.d(TAG, "NetworkController=" + nc);
        mNC = nc;
    }

    public void setSecurityController(SecurityController sc) {
        if (DEBUG) Log.d(TAG, "SecurityController=" + sc);
        mSC = sc;
        mSC.addCallback(this);
        mVpnVisible = mSC.isVpnEnabled();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mWideTypeIconStartPadding = getContext().getResources().getDimensionPixelSize(
                R.dimen.wide_type_icon_start_padding);
        mSecondaryTelephonyPadding = getContext().getResources().getDimensionPixelSize(
                R.dimen.secondary_telephony_padding);
        mEndPadding = getContext().getResources().getDimensionPixelSize(
                R.dimen.signal_cluster_battery_padding);
        mEndPaddingNothingVisible = getContext().getResources().getDimensionPixelSize(
                R.dimen.no_signal_cluster_battery_padding);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mVpn            = (ImageView) findViewById(R.id.vpn);
        mEthernetGroup  = (ViewGroup) findViewById(R.id.ethernet_combo);
        mEthernet       = (ImageView) findViewById(R.id.ethernet);
        mEthernetDark   = (ImageView) findViewById(R.id.ethernet_dark);
        mWifiGroup      = (ViewGroup) findViewById(R.id.wifi_combo);
        mWifi           = (ImageView) findViewById(R.id.wifi_signal);
        mWifiDark       = (ImageView) findViewById(R.id.wifi_signal_dark);
        mWifiIn = (ImageView) findViewById(R.id.wifi_in);
        mWifiOut = (ImageView) findViewById(R.id.wifi_out);
        mAirplane       = (ImageView) findViewById(R.id.airplane);
        mNoSims         = (ImageView) findViewById(R.id.no_sims);
        mNoSimsDark     = (ImageView) findViewById(R.id.no_sims_dark);
        mNoSimsCombo    =             findViewById(R.id.no_sims_combo);
        // SPRD: add for bug 523383
        mEthernetSpacer =             findViewById(R.id.ethernet_spacer);
        mWifiAirplaneSpacer =         findViewById(R.id.wifi_airplane_spacer);
        mWifiSignalSpacer =           findViewById(R.id.wifi_signal_spacer);
        // SPRD: Add VoLte icon for bug 509601.
        mVolte          = (ImageView) findViewById(R.id.volte);
        // SPRD: Add HD audio icon in cucc for bug 536924.
        mHdVoice        = (ImageView) findViewById(R.id.hd_voice);
        mMobileSignalGroup = (LinearLayout) findViewById(R.id.mobile_signal_group_ex);
        for (PhoneState state : mPhoneStates) {
            mMobileSignalGroup.addView(state.mMobileGroup);
        }
        TunerService.get(mContext).addTunable(this, StatusBarIconController.ICON_BLACKLIST);

        apply();
        applyIconTint();
    }

    @Override
    protected void onDetachedFromWindow() {
        mVpn            = null;
        mEthernetGroup  = null;
        mEthernet       = null;
        mWifiGroup      = null;
        mWifi           = null;
        mWifiOut = null;
        mWifiIn = null;
        mAirplane       = null;
        // SPRD: Add VoLte icon for bug 509601.
        mVolte          = null;
        // SPRD: Add HD audio icon in cucc for bug 536924.
        mHdVoice        = null;
        mMobileSignalGroup.removeAllViews();
        mMobileSignalGroup = null;
        TunerService.get(mContext).removeTunable(this);

        super.onDetachedFromWindow();
    }

    // From SecurityController.
    @Override
    public void onStateChanged() {
        post(new Runnable() {
            @Override
            public void run() {
                mVpnVisible = mSC.isVpnEnabled();
                apply();
            }
        });
    }

    @Override
    public void setWifiIndicators(boolean enabled, IconState statusIcon, IconState qsIcon,
            boolean activityIn, boolean activityOut, String description) {
        mWifiVisible = statusIcon.visible && !mBlockWifi;
        mWifiStrengthId = statusIcon.icon;
        mWifiDescription = statusIcon.contentDescription;
        isWifiIn = activityIn;
        isWifiOut = activityOut;

        apply();
    }

    /* SPRD: Add VoLte icon for bug 509601. @{ */
    public void setVoLteIndicators(boolean enabled) {
        // SPRD: Reliance UI spec 1.7. See bug #522899.
        mVolteIconId = enabled ? SystemUIPluginsHelper.getInstance().getVoLTEIcon() : 0;
        mImsRegisterPhoneId = TelephonyManager.from(mContext).getPrimaryCard();
        apply();
    }
    /* @} */

    /* SPRD: Add HD audio icon in cucc for bug 536924. @{ */
    public void setHdVoiceIndicators(boolean enabled) {
        mHdVoiceDraw = enabled ? SystemUIPluginsHelper.getInstance().getHdVoiceDraw() : null;
        mHdVoiceVisible = enabled;
        mImsRegisterPhoneId = TelephonyManager.from(mContext).getPrimaryCard();
        apply();
    }
    /* @} */

    @Override
    public void setMobileDataIndicators(IconState statusIcon, IconState qsIcon, int statusType,
            int qsType, boolean activityIn, boolean activityOut, String typeContentDescription,
            String description, boolean isWide, int subId) {
        PhoneState state = getState(subId);
        if (state == null) {
            return;
        }
        state.mMobileVisible = statusIcon.visible && !mBlockMobile;
        state.mMobileStrengthId = statusIcon.icon;
        state.mMobileTypeId = statusType;
        state.mMobileDescription = statusIcon.contentDescription;
        state.mMobileTypeDescription = typeContentDescription;
        state.mIsMobileTypeIconWide = statusType != 0 && isWide;
        state.mActivityIn = activityIn;
        state.mActivityOut = activityOut;
        apply();
    }

    /* SPRD: modify by BUG 491086 ; modify by BUG 517092, add roamIcon @{ */
    @Override
    public void setMobileDataIndicators(IconState statusIcon, IconState qsIcon, int statusType,
            int qsType, boolean activityIn, boolean activityOut, String typeContentDescription,
            String description, boolean isWide, int subId, boolean dataConnect, int colorScheme,
            int roamIcon) {
        PhoneState state = getState(subId);
        if (state == null) {
            return;
        }
        state.mMobileVisible = statusIcon.visible && !mBlockMobile;
        state.mMobileStrengthId = statusIcon.icon;
        state.mMobileTypeId = statusType;
        state.mMobileRoamId = roamIcon;
        state.mMobileDescription = statusIcon.contentDescription;
        state.mMobileTypeDescription = typeContentDescription;
        state.mIsMobileTypeIconWide = statusType != 0 && isWide;
        state.mActivityIn = activityIn;
        state.mActivityOut = activityOut;
        state.mDataConnect = dataConnect;
        state.mSignalColor = colorScheme;
        apply();
    }
    /* @} */

    /* SPRD: Reliance UI spec 1.7. See bug #522899. @{ */
    @Override
    public void setMobileDataIndicators(IconState statusIcon, IconState qsIcon, int statusType,
            int qsType, boolean activityIn, boolean activityOut, String typeContentDescription,
            String description, boolean isWide, int subId, boolean dataConnect, int colorScheme,
            int roamIcon, int imsregIcon, boolean isFourG) {
        PhoneState state = getState(subId);
        if (state == null) {
            return;
        }
        state.mMobileVisible = statusIcon.visible && !mBlockMobile;
        state.mMobileStrengthId = statusIcon.icon;
        state.mMobileTypeId = statusType;
        state.mMobileRoamId = roamIcon;
        state.mMobileImsregId = imsregIcon;
        state.isFourGLTE = isFourG;
        state.mMobileDescription = statusIcon.contentDescription;
        state.mMobileTypeDescription = typeContentDescription;
        state.mIsMobileTypeIconWide = statusType != 0 && isWide;
        state.mActivityIn = activityIn;
        state.mActivityOut = activityOut;
        state.mDataConnect = dataConnect;
        state.mSignalColor = colorScheme;
        apply();
    }
    /* @} */

    @Override
    public void setEthernetIndicators(IconState state) {
        mEthernetVisible = state.visible && !mBlockEthernet;
        mEthernetIconId = state.icon;
        mEthernetDescription = state.contentDescription;

        apply();
    }

    @Override
    public void setNoSims(boolean show) {
        mNoSimsVisible = show && !mBlockMobile;
    }

    @Override
    public void setSubs(List<SubscriptionInfo> subs) {
        // SPRD : Modify for bug 516021
        if (hasCorrectSubs(subs) && subs.size() != 0) {
            return;
        }
        /* SPRD: Bug 474688 Add for SIM hot plug feature @{ */
        int validSimCount = 0;
        int activeSubSize = subs.size();
        boolean isHotSwapSupported = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_hotswapCapable);
        ArrayList<PhoneState> phoneStates = new ArrayList<PhoneState>(mPhoneStates);
        for(PhoneState state : mPhoneStates) {
            if(SubscriptionManager.isValidSubscriptionId(state.mSubId)) {
                validSimCount ++;
            }
        }
        Log.d(TAG,"validSimCount = " + validSimCount + ", subs.size = " + activeSubSize);
        if(isHotSwapSupported && validSimCount > 1 && activeSubSize == 0){
            Log.e(TAG,"invalid state, do nothing and return");
            return;
        }
        boolean simCountChange = !(validSimCount == activeSubSize);
        /* @} */
        // Clear out all old subIds.
        mPhoneStates.clear();
        if (mMobileSignalGroup != null) {
            mMobileSignalGroup.removeAllViews();
        }
        /* SPRD: modify by bug474984 @{ */
        final int n = TelephonyManager.from(mContext).getPhoneCount();
        for (int i = 0; i < n; i++) {
            SubscriptionInfo subInfo = findRecordByPhoneId(subs, i);
            int subId = subInfo != null ? subInfo.getSubscriptionId()
                    : SubscriptionManager.INVALID_SUBSCRIPTION_ID - i;
            /* SPRD: Bug 474688 Add for SIM hot plug feature @{ */
            PhoneState lastState = null ;
            if(SubscriptionManager.isValidSubscriptionId(subId)) {
                for(PhoneState state : phoneStates) {
                    if(SubscriptionManager.isValidSubscriptionId(state.mSubId) && state.mSubId == subId) {
                        lastState = state;
                        break;
                    }
                }
            }
            if(isHotSwapSupported && simCountChange && lastState != null) {
                if (mMobileSignalGroup != null) {
                    mMobileSignalGroup.addView(lastState.mMobileGroup);
                }
                mPhoneStates.add(lastState);
            } else {
                lastState = inflatePhoneState(subId);
                /* SPRD: display systemui with sim color. {@ */
                if (lastState.mColorSimEnabled && subInfo != null) {
                    lastState.mSignalColor = subInfo.getIconTint();
                }
                /* @} */
            }
            /* @} */
        }
        /* @} */
        if (isAttachedToWindow()) {
            applyIconTint();
        }
    }

    /* SPRD: modify by bug474984 @{ */
    private SubscriptionInfo findRecordByPhoneId(List<SubscriptionInfo> subs, int phoneId) {
        if (subs != null) {
            final int length = subs.size();
            for (int i=0 ;i <length ; i++ ) {
                final SubscriptionInfo sir = subs.get(i);
                if (sir.getSimSlotIndex() == phoneId) {
                    return sir;
                }
            }
        }
        return null;
    }
    /* @} */

    private boolean hasCorrectSubs(List<SubscriptionInfo> subs) {
        final int N = subs.size();
        if (N != mPhoneStates.size()) {
            return false;
        }
        for (int i = 0; i < N; i++) {
            if (mPhoneStates.get(i).mSubId != subs.get(i).getSubscriptionId()) {
                return false;
            }
        }
        return true;
    }

    private PhoneState getState(int subId) {
        for (PhoneState state : mPhoneStates) {
            if (state.mSubId == subId) {
                return state;
            }
        }
        Log.e(TAG, "Unexpected subscription " + subId);
        return null;
    }

    private PhoneState inflatePhoneState(int subId) {
        PhoneState state = new PhoneState(subId, mContext);
        if (mMobileSignalGroup != null) {
            mMobileSignalGroup.addView(state.mMobileGroup);
        }
        mPhoneStates.add(state);
        return state;
    }

    @Override
    public void setIsAirplaneMode(IconState icon) {
        mIsAirplaneMode = icon.visible && !mBlockAirplane;
        mAirplaneIconId = icon.icon;
        mAirplaneContentDescription = icon.contentDescription;

        apply();
    }

    @Override
    public void setMobileDataEnabled(boolean enabled) {
        // Don't care.
    }

    @Override
    public boolean dispatchPopulateAccessibilityEventInternal(AccessibilityEvent event) {
        // Standard group layout onPopulateAccessibilityEvent() implementations
        // ignore content description, so populate manually
        if (mEthernetVisible && mEthernetGroup != null &&
                mEthernetGroup.getContentDescription() != null)
            event.getText().add(mEthernetGroup.getContentDescription());
        if (mWifiVisible && mWifiGroup != null && mWifiGroup.getContentDescription() != null)
            event.getText().add(mWifiGroup.getContentDescription());
        for (PhoneState state : mPhoneStates) {
            state.populateAccessibilityEvent(event);
        }
        return super.dispatchPopulateAccessibilityEventInternal(event);
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);

        if (mEthernet != null) {
            mEthernet.setImageDrawable(null);
            mEthernetDark.setImageDrawable(null);
            mLastEthernetIconId = -1;
        }

        if (mWifi != null) {
            mWifi.setImageDrawable(null);
            mWifiDark.setImageDrawable(null);
            mLastWifiStrengthId = -1;
        }

        /* SPRD: Add VoLte icon for bug 509601. @{ */
        if (mVolte != null) {
            mVolte.setImageDrawable(null);
            mLastVolteIconId = -1;
        }
        /* @} */

        /* SPRD: Add HD audio icon in cucc for bug 536924. @{ */
        if (mHdVoice != null) {
            mHdVoice.setImageDrawable(null);
        }
        /* @} */

        for (PhoneState state : mPhoneStates) {
            if (state.mMobile != null) {
                state.mMobile.setImageDrawable(null);
            }
            if (state.mMobileType != null) {
                state.mMobileType.setImageDrawable(null);
            }
            /* SPRD: add for bug 517092 @{ */
            if (state.mMobileRoam != null) {
                state.mMobileRoam.setImageDrawable(null);
            }
            /* @} */
            /* SPRD: Reliance UI spec 1.7. See bug #522899. @{ */
            if (state.mMobileImsreg != null) {
                state.mMobileImsreg.setImageDrawable(null);
            }
            /* @} */
        }

        if (mAirplane != null) {
            mAirplane.setImageDrawable(null);
            mLastAirplaneIconId = -1;
        }

        apply();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    // Run after each indicator change.
    private void apply() {
        if (mWifiGroup == null) return;

        mVpn.setVisibility(mVpnVisible ? View.VISIBLE : View.GONE);
        if (DEBUG) Log.d(TAG, String.format("vpn: %s", mVpnVisible ? "VISIBLE" : "GONE"));

        if (mEthernetVisible) {
            if (mLastEthernetIconId != mEthernetIconId) {
                mEthernet.setImageResource(mEthernetIconId);
                mEthernetDark.setImageResource(mEthernetIconId);
                mLastEthernetIconId = mEthernetIconId;
            }
            mEthernetGroup.setContentDescription(mEthernetDescription);
            mEthernetGroup.setVisibility(View.VISIBLE);
            // SPRD: add for bug 523383
            mEthernetSpacer.setVisibility(View.VISIBLE);
        } else {
            mEthernetGroup.setVisibility(View.GONE);
            // SPRD: add for bug 523383
            mEthernetSpacer.setVisibility(View.GONE);
        }

        if (DEBUG) Log.d(TAG,
                String.format("ethernet: %s",
                    (mEthernetVisible ? "VISIBLE" : "GONE")));


        if (mWifiVisible) {
            mWifiIn.setVisibility(isWifiIn ? View.VISIBLE : View.INVISIBLE);
            mWifiOut.setVisibility(isWifiOut ? View.VISIBLE
                    : View.INVISIBLE);
            if (mWifiStrengthId != mLastWifiStrengthId) {
                mWifi.setImageResource(mWifiStrengthId);
                mWifiDark.setImageResource(mWifiStrengthId);
                mLastWifiStrengthId = mWifiStrengthId;
            }
            mWifiGroup.setContentDescription(mWifiDescription);
            mWifiGroup.setVisibility(View.VISIBLE);
        } else {
            mWifiGroup.setVisibility(View.GONE);
        }

        if (DEBUG) Log.d(TAG,
                String.format("wifi: %s sig=%d",
                    (mWifiVisible ? "VISIBLE" : "GONE"),
                    mWifiStrengthId));

        boolean anyMobileVisible = false;
        int firstMobileTypeId = 0;
        for (PhoneState state : mPhoneStates) {
            if (state.apply(anyMobileVisible)) {
                if (!anyMobileVisible) {
                    firstMobileTypeId = state.mMobileTypeId;
                    anyMobileVisible = true;
                }
            }
        }

        /* SPRD: Add VoLte icon for bug 509601. @{ */
        mVolteVisible = mIsSupportVolte;
        if (mVolteVisible && !mIsAirplaneMode && mVolteIconId != 0) {
            if (mLastVolteIconId != mVolteIconId) {
                mVolte.setImageResource(mVolteIconId);
                mLastVolteIconId = mVolteIconId;
            }
            mVolte.setVisibility(View.VISIBLE);
        } else {
            mVolte.setVisibility(View.GONE);
        }
        /* @} */

        /* SPRD: Add HD audio icon in cucc for bug 536924. @{ */
        if (mHdVoiceVisible && !mIsAirplaneMode && mHdVoiceDraw != null) {
            mHdVoice.setImageDrawable(mHdVoiceDraw);
            mHdVoice.setVisibility(View.VISIBLE);
        } else {
            mHdVoice.setVisibility(View.GONE);
        }
        /* @} */

        if (mIsAirplaneMode) {
            if (mLastAirplaneIconId != mAirplaneIconId) {
                mAirplane.setImageResource(mAirplaneIconId);
                mLastAirplaneIconId = mAirplaneIconId;
            }
            mAirplane.setContentDescription(mAirplaneContentDescription);
            mAirplane.setVisibility(View.VISIBLE);
        } else {
            mAirplane.setVisibility(View.GONE);
        }

        if (mIsAirplaneMode && mWifiVisible) {
            mWifiAirplaneSpacer.setVisibility(View.VISIBLE);
        } else {
            mWifiAirplaneSpacer.setVisibility(View.GONE);
        }

        if (((anyMobileVisible && firstMobileTypeId != 0) || mNoSimsVisible) && mWifiVisible) {
            mWifiSignalSpacer.setVisibility(View.VISIBLE);
        } else {
            mWifiSignalSpacer.setVisibility(View.GONE);
        }

        mNoSimsCombo.setVisibility(View.GONE);

        boolean anythingVisible = mNoSimsVisible || mWifiVisible || mIsAirplaneMode
                || anyMobileVisible || mVpnVisible || mEthernetVisible;
        setPaddingRelative(0, 0, anythingVisible ? mEndPadding : mEndPaddingNothingVisible, 0);
    }

    public void setIconTint(int tint, float darkIntensity) {
        boolean changed = tint != mIconTint || darkIntensity != mDarkIntensity;
        mIconTint = tint;
        mDarkIntensity = darkIntensity;
        if (changed && isAttachedToWindow()) {
            applyIconTint();
        }
    }

    private void applyIconTint() {
        setTint(mVpn, mIconTint);
        setTint(mAirplane, mIconTint);
        applyDarkIntensity(mDarkIntensity, mNoSims, mNoSimsDark);
        applyDarkIntensity(mDarkIntensity, mWifi, mWifiDark);
        applyDarkIntensity(mDarkIntensity, mEthernet, mEthernetDark);
        for (int i = 0; i < mPhoneStates.size(); i++) {
            mPhoneStates.get(i).setIconTint(mIconTint, mDarkIntensity);
        }
    }

    private void applyDarkIntensity(float darkIntensity, View lightIcon, View darkIcon) {
        lightIcon.setAlpha(1 - darkIntensity);
        darkIcon.setAlpha(darkIntensity);
    }

    private void setTint(ImageView v, int tint) {
        v.setImageTintList(ColorStateList.valueOf(tint));
    }

    /* SPRD: modify for bug495410 @{ */
    @Override
    public void setDeactiveIcon(int subId) {
        for (PhoneState state: mPhoneStates) {
            if (state.mSubId == subId) {
                state.setDeactiveIcon();
                break;
            }
        }
    }
    /* @} */

    /* SPRD: modify by bug474984 @{ */
    private class PhoneState {
        private final int mSubId;
        private boolean mMobileVisible = true;
        private int mMobileStrengthId = SystemUIPluginsHelper.getInstance().getNoSimIconId();
        // SPRD: Reliance UI spec 1.7. See bug #522899.
        private int mViewGroupLayout = SystemUIPluginsHelper.getInstance().getMobileGroupLayout();
        // SPRD: modify for bug 517092
        // SPRD: Reliance UI spec 1.7. See bug #522899.
        private int mMobileTypeId = 0, mMobileDataInOutId = 0, mMobileCardId = 0,
                mMobileRoamId = 0, mMobileImsregId = 0;
        private boolean mIsMobileTypeIconWide;
        // SPRD: Reliance UI spec 1.7. See bug #522899.
        private boolean isFourGLTE;
        private String mMobileDescription, mMobileTypeDescription;
        private boolean mDataConnect;
        // SPRD: modify for bug495410
        private TelephonyManager mTelephonyManager;

        private ViewGroup mMobileGroup;
        // SPRD: modify for bug 517092
        // SPRD: Reliance UI spec 1.7. See bug #522899.
        private ImageView mMobile, mMobileDark, mMobileDataInOut, mMobileType,
                mMobileCard, mMobileRoam, mMobileImsreg;
        private boolean mColorfulMobileSignal, mActivityIn, mActivityOut;
        // SPRD: modify by BUG 494698
        private int mSignalColor, mPreSignalColor = SystemUIPluginsHelper.ABSENT_SIM_COLOR;
        private boolean mColorSimEnabled;
        private SystemUiConfig mSystemUiConfig;

        public PhoneState(int subId, Context context) {
            Log.d(TAG, "Create PhoneState subId = " + subId);
            // SPRD: Reliance UI spec 1.7. See bug #522899.
            ViewGroup root = (ViewGroup) LayoutInflater.from(context)
                    .inflate(mViewGroupLayout, null);
            setViews(root);
            mSubId = subId;
            mMobileCardId = SystemUIPluginsHelper.getInstance().getSimCardIconId(subId);
            mColorfulMobileSignal = getResources().getBoolean(R.bool.enable_signal_strenth_color);
            // SPRD: modify for bug495410
            mTelephonyManager = TelephonyManager.from(context);
            /* SPRD: display systemui with sim color {@ */
            mSystemUiConfig = SystemUiConfig.getInstance(context);
            mColorSimEnabled = mSystemUiConfig.shouldShowColorfulSystemUI();
            /* @} */
        }

        public void setViews(ViewGroup root) {
            mMobileGroup    = root;
            mMobile         = (ImageView) root.findViewById(R.id.mobile_signal);
            mMobileDataInOut = (ImageView) root.findViewById(R.id.mobile_data_in_out);
            mMobileDark     = (ImageView) root.findViewById(R.id.mobile_signal_dark);
            mMobileType     = (ImageView) root.findViewById(R.id.mobile_type);
            mMobileCard     = (ImageView) root.findViewById(R.id.mobile_card);
            // SPRD: modify for bug 517092
            mMobileRoam     = (ImageView) root.findViewById(R.id.mobile_roam_type);
            // SPRD: Reliance UI spec 1.7. See bug #522899.
            mMobileImsreg   = (ImageView) root.findViewById(R.id.mobile_imsreg);
        }

        public boolean apply(boolean isSecondaryIcon) {
            if (mMobileVisible && !mIsAirplaneMode) {
                // SPRD: modify for bug495410
                updateStandbyIcon();
                mMobile.setImageResource(mMobileStrengthId);
                if (mColorfulMobileSignal) {
                    /* SPRD: Add for SimSignal color change follow sim color @{ */
                    if (mColorSimEnabled &&
                            mPreSignalColor != mSignalColor) {
                        Log.d(TAG, String.format("apply color for sub%d, 0x%08x -> 0x%08x",
                                    mSubId, mPreSignalColor, mSignalColor));
                        mPreSignalColor = mSignalColor;
                        mMobile.setColorFilter(mPreSignalColor);
                        /* SPRD: modify by BUG 474976 @{ */
                        mMobileCard.setColorFilter(mSignalColor);
                        mMobileType.setColorFilter(mSignalColor);
                        mMobileDataInOut.setColorFilter(mSignalColor);
                        /* @} */
                    }
                    /* @} */
                }
                Drawable mobileDrawable = mMobile.getDrawable();
                if (mobileDrawable instanceof Animatable) {
                    Animatable ad = (Animatable) mobileDrawable;
                    if (!ad.isRunning()) {
                        ad.start();
                    }
                }

                mMobileDark.setImageResource(mMobileStrengthId);
                Drawable mobileDarkDrawable = mMobileDark.getDrawable();
                if (mobileDarkDrawable instanceof Animatable) {
                    Animatable ad = (Animatable) mobileDarkDrawable;
                    if (!ad.isRunning()) {
                        ad.start();
                    }
                }

                // SPRD: modify by BUG 474976
                mMobileCard.setImageResource(mMobileCardId);
                mMobileType.setImageResource(mMobileTypeId);
                /* SPRD: modify by BUG 491086 @{ */
                /* SPRD: Reliance UI spec 1.7. See bug #522899. @{ */
                if (mDataConnect) {
                    if (mActivityIn && mActivityOut) {
                        mMobileDataInOutId = SystemUIPluginsHelper.getInstance().getDataInOutIcon();
                    } else if (mActivityIn) {
                        mMobileDataInOutId = SystemUIPluginsHelper.getInstance().getDataInIcon();
                    } else if (mActivityOut) {
                        mMobileDataInOutId = SystemUIPluginsHelper.getInstance().getDataOutIcon();
                    } else {
                        mMobileDataInOutId = SystemUIPluginsHelper.getInstance()
                                .getDataDefaultIcon();
                    }
                } else {
                    mMobileDataInOutId = 0;
                }
                /* @} */
                /* @} */

                // SPRD: modify for bug 517092
                /* SPRD: Reliance UI spec 1.7. See bug #522899. @{ */
                if (isRelianceBoard && mMobileImsregId != 0 && mMobileRoamId == 0) {
                    mMobileRoam.setImageResource(mMobileImsregId);
                } else if (isRelianceBoard && mMobileRoamId == 0 && !isFourGLTE
                        && mMobileTypeId != 0
                        && mMobileDataInOutId == 0) {
                    mMobileRoam.setImageResource(mMobileTypeId);
                } else {
                    mMobileRoam.setImageResource(mMobileRoamId);
                }
                /* @} */

                mMobileDataInOut.setImageResource(mMobileDataInOutId);

                mMobileGroup.setContentDescription(mMobileTypeDescription
                        + " " + mMobileDescription);
                mMobileGroup.setVisibility(View.VISIBLE);
            } else {
                mMobileGroup.setVisibility(View.GONE);
            }

            // When this isn't next to wifi, give it some extra padding between the signals.
            mMobileGroup.setPaddingRelative(isSecondaryIcon ? mSecondaryTelephonyPadding : 0,
                    0, 0, 0);
            mMobile.setPaddingRelative(mIsMobileTypeIconWide ? mWideTypeIconStartPadding : 0,
                    0, 0, 0);

            if (DEBUG) Log.d(TAG, String.format("mobile: %s sig=%d typ=%d",
                        (mMobileVisible ? "VISIBLE" : "GONE"), mMobileStrengthId, mMobileTypeId));

            /* SPRD: Reliance UI spec 1.7. See bug #522899. @{ */
            if (isRelianceBoard && mMobileRoamId == 0 && mMobileImsregId != 0) {
                mMobileType.setVisibility(mMobileTypeId != 0 ? View.VISIBLE : View.GONE);
                mMobileRoam.setVisibility(View.VISIBLE);
            } else if (isRelianceBoard && mMobileRoamId == 0 && !isFourGLTE && mMobileTypeId != 0
                    && mMobileDataInOutId == 0) {
                mMobileType.setVisibility(View.GONE);
                mMobileRoam.setVisibility(View.VISIBLE);
            } else {
                mMobileType.setVisibility(mMobileTypeId != 0 ? View.VISIBLE : View.GONE);
                // SPRD: add for bug 517092
                mMobileRoam.setVisibility(mMobileRoamId != 0 ? View.VISIBLE : View.GONE);
            }
            /* @} */
            mMobileCard.setVisibility(mMobileCardId != 0 ? View.VISIBLE : View.GONE);
            mMobileDark.setPaddingRelative(mIsMobileTypeIconWide ? mWideTypeIconStartPadding : 0,
                    0, 0, 0);
            mMobileDark.setVisibility(View.GONE);
            mMobileDataInOut.setVisibility(mMobileTypeId != 0 ? View.VISIBLE : View.GONE);
            return mMobileVisible;
        }

        public void populateAccessibilityEvent(AccessibilityEvent event) {
            if (mMobileVisible && mMobileGroup != null
                    && mMobileGroup.getContentDescription() != null) {
                event.getText().add(mMobileGroup.getContentDescription());
            }
        }

        /* SPRD: modify for bug495410 @{ */
        public void setDeactiveIcon() {
            int phoneId = SubscriptionManager.getPhoneId(mSubId);
            if (!mTelephonyManager.isSimStandby(phoneId)) {
                mMobileStrengthId = SystemUIPluginsHelper.getInstance().getSimStandbyIconId();
                mMobile.setColorFilter(null);
                /* SPRD: modify for bug474987 @{ */
                mPreSignalColor = -1;
                mSignalColor = -1;
                /* @} */
                apply(false);
            }
        }

        private void updateStandbyIcon() {
            int phoneId = SubscriptionManager.getPhoneId(mSubId);
            if (mTelephonyManager.isSimStandby(phoneId)
                    && mMobileStrengthId == SystemUIPluginsHelper
                    .getInstance().getSimStandbyIconId()) {
                mMobileStrengthId = SystemUIPluginsHelper.getInstance().getNoServiceIconId();
                apply(false);
            }
        }
        /* @} */

        public void setIconTint(int tint, float darkIntensity) {
            /* SPRD: Add for bug 560985. @{ */
            // applyDarkIntensity(darkIntensity, mMobile, mMobileDark);
            setTint(mMobileType, tint);
            setTint(mMobile, tint);
            setTint(mMobileDataInOut, tint);
            setTint(mMobileCard, tint);
            setTint(mMobileRoam, tint);
            /* @} */
        }
    }
    /* @} */

    /* SPRD: Add for SimSignal color change follow sim color @{ */
    @Override
    public void setSimSignalColor(int subId, int simColor) {
        PhoneState state = getState(subId);
        if (state != null) {
            state.mSignalColor = simColor;
            state.mMobile.setColorFilter(simColor);
        }
    }
    /* @} */

    /* SPRD: modify by BUG 549167 @{ */
    @Override
    public void refreshIconsIfSimAbsent(int phoneId) {
        int primaryCard = TelephonyManager.from(mContext).getPrimaryCard();
        Log.d(TAG, "refreshIconsIfSimAbsent phoneId : " + phoneId + "; mImsRegisterPhoneId : "
                + mImsRegisterPhoneId);
        if (phoneId == mImsRegisterPhoneId) {
            mHdVoiceDraw = null;
            mHdVoiceVisible = false;
            mVolteIconId = 0;
            mHdVoice.setVisibility(View.GONE);
            mVolte.setVisibility(View.GONE);
        }
    }
    /* @} */
}
