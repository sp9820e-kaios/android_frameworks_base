package com.android.telephony;

import android.app.AddonManager;
import android.util.Log;
import com.android.internal.R;

/**
 * for signal strength plugin bug#474988
 */
public class SignalStrengthUtils {

    private static final String TAG = "SignalStrengthUtils";
    private static SignalStrengthUtils mInstance;
    static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    public SignalStrengthUtils(){
    }

    public synchronized static SignalStrengthUtils getInstance(){
        if(mInstance != null){
            return mInstance;
        }
        mInstance = (SignalStrengthUtils) AddonManager.getDefault().
                getAddon(R.string.feature_signalstrength, SignalStrengthUtils.class);
        return mInstance;
    }

    public int processLteLevel(int level, int lteRsrp){
        if(DEBUG) Log.d(TAG, "processLteLevel: empty method");
        return level;
    }
}