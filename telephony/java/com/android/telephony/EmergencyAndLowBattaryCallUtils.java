package com.android.telephony;

import android.content.Intent;
import android.app.AddonManager;
import android.util.Log;
import android.content.Context;

import com.android.internal.R;

import android.telecom.Call;
import android.telecom.VideoProfile;

public class EmergencyAndLowBattaryCallUtils {

    private static final String TAG = "EmergencyAndLowBattaryCallUtils";
    static EmergencyAndLowBattaryCallUtils sInstance;

    public static EmergencyAndLowBattaryCallUtils getInstance() {
        Log.d(TAG, "enter EmergencyAndLowBattaryCallUtils");
        if (sInstance != null)
            return sInstance;
        sInstance = (EmergencyAndLowBattaryCallUtils) AddonManager.getDefault().getAddon(R.string.feature_AddonEmergencyAndLowBattaryCallUtils,
                EmergencyAndLowBattaryCallUtils.class);
        return sInstance;
    }

    public EmergencyAndLowBattaryCallUtils() {
    }

    public boolean isBatteryLow() {
        Log.d(TAG, "isBatteryLow = false");
        return false;
    }

    /* SPRD: Add function for bug579975 @{ */
    public void showLowBatteryDialDialog(Context context,Intent intent,boolean isDialingByDialer) {
        Log.d(TAG, "showLowBatteryDialDialog");
    }

    public void showLowBatteryInCallDialog(Context context,android.telecom.Call telecomCall,int mpcMode) {
        Log.d(TAG, "showLowBatteryInCallDialog");
    }

    public void showLowBatteryChangeToVideoDialog(android.telecom.Call telecomCall,VideoProfile videoProfile) {
        Log.d(TAG, "showLowBatteryChangeToVideoDialog");
    }
    /* @} */

    public String AddEmergencyNO(int key,String fastcall) {
        Log.d(TAG, "AddEmergencyNO");
        // SPRD: modify for bug526629
        return fastcall;
    }
}
