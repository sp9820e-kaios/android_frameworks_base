/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.telephony.SubscriptionInfo;

import com.android.systemui.statusbar.policy.NetworkController.IconState;
import com.android.systemui.statusbar.policy.NetworkController.SignalCallback;

import java.util.List;


/**
 * Provides empty implementations of SignalCallback for those that only want some of
 * the callbacks.
 */
public class SignalCallbackAdapter implements SignalCallback {

    @Override
    public void setWifiIndicators(boolean enabled, IconState statusIcon, IconState qsIcon,
            boolean activityIn, boolean activityOut, String description) {
    }

    /* SPRD: modify for bug495410 @{ */
    @Override
    public void setDeactiveIcon(int subId) {
    }
    /* @} */

    /* SPRD: modify by BUG 491086 ; modify by BUG 517092, add roamIcon @{ */
    @Override
    public void setMobileDataIndicators(IconState statusIcon, IconState qsIcon, int statusType,
            int qsType, boolean activityIn, boolean activityOut, String typeContentDescription,
            String description, boolean isWide, int subId, boolean dataConnect,
            int colorScheme, int roamIcon) {
    }
    /* @} */

    @Override
    public void setMobileDataIndicators(IconState statusIcon, IconState qsIcon, int statusType,
            int qsType, boolean activityIn, boolean activityOut, String typeContentDescription,
            String description, boolean isWide, int subId) {
    }

    /* SPRD: Reliance UI spec 1.7. See bug #522899. @{ */
    public void setMobileDataIndicators(IconState statusIcon, IconState qsIcon, int statusType,
            int qsType, boolean activityIn, boolean activityOut, String typeContentDescription,
            String description, boolean isWide, int subId, boolean dataConnect, int colorScheme,
            int roamIcon, int imsregIcon, boolean isFourG) {
    }
    /* @} */

    @Override
    public void setSubs(List<SubscriptionInfo> subs) {
    }

    @Override
    public void setNoSims(boolean show) {
    }

    @Override
    public void setEthernetIndicators(IconState icon) {
    }

    // SPRD: Add VoLte icon for bug 509601.
    public void setVoLteIndicators(boolean enabled) {
    }

    // SPRD: Add HD audio icon for bug 536924.
    public void setHdVoiceIndicators(boolean enabled) {
    }

    @Override
    public void setIsAirplaneMode(IconState icon) {
    }

    @Override
    public void setMobileDataEnabled(boolean enabled) {
    }

    /* SPRD: Add for SimSignal color change follow sim color @{ */
    @Override
    public void setSimSignalColor(int subId, int simColor) {
    }
    /* @} */

    /* SPRD: modify by BUG 549167 @{ */
    @Override
    public void refreshIconsIfSimAbsent(int phoneId) {
    }
    /* @} */

}
