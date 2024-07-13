
package com.sprd.keyguard;

import android.app.AddonManager;
import android.content.Context;
import android.util.Log;
import com.android.keyguard.R;
/* SPRD: modify by bug511349 @{ */
public class SimlockStatusHelper {
    static SimlockStatusHelper sInstance;
    public static final String TAG = "SimlockStatusHelper";

    public SimlockStatusHelper() {
    }

    public static SimlockStatusHelper getInstance() {
        if (sInstance != null)
            return sInstance;
        sInstance = (SimlockStatusHelper) AddonManager.getDefault().getAddon(
                R.string.plugin_simlock_status_operator, SimlockStatusHelper.class);
        return sInstance;
    }
    /* SPRD: modify by bug511349 @{ */
    public int getSimLockStringId(int slotId){
        Log.d(TAG, "getSimLockStringId = -1" );
        return -1;
    }
    /* @} */
}
/* @} */