
/*
 * SPRD: Add by BUG 540847
 *
 * To manage the carrierconfig for keyguard
 *
 */
package com.sprd.keyguard;

import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;

public class KeyguardConfig {
    private static KeyguardConfig sInstance;

    private Context mContext;
    private CarrierConfigManager mConfigManager;
    private PersistableBundle mPersistableBundle;

    /**
     * Get the singleton instance of KeyguardConfig
     */
    public synchronized static KeyguardConfig getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new KeyguardConfig(context);
        }
        return sInstance;
    }

    public boolean shouldShowColorfulSystemUI() {
        ensureConfig();

        boolean defValue = false;
        if (mPersistableBundle != null) {
            return mPersistableBundle.getBoolean(CarrierConfigManager
                    .KEY_SYSTEMUI_CARRIER_LABEL_WITH_SIMCOLOR_BOOL);
        }
        return defValue;
    }

    public String getOperatorSeparatorLabel() {
        ensureConfig();

        String defValue = "";
        if (mPersistableBundle != null) {
            return mPersistableBundle.getString(CarrierConfigManager
                    .KEY_OPERATOR_SEPARATOR_LABEL);
        }
        return defValue;
    }

    private KeyguardConfig(Context context) {
        mContext = context;
        mConfigManager = (CarrierConfigManager) mContext.getSystemService(
                Context.CARRIER_CONFIG_SERVICE);
    }

    public void dispose() {
        mConfigManager = null;
        mPersistableBundle = null;
    }

    private void ensureConfig() {
        if (mPersistableBundle == null) {
            if (mConfigManager == null) {
                mConfigManager = (CarrierConfigManager) mContext.getSystemService(
                        Context.CARRIER_CONFIG_SERVICE);
            }

            if (mConfigManager != null) {
                mPersistableBundle = mConfigManager.getConfigForDefaultPhone();
            }
        }
    }
}
