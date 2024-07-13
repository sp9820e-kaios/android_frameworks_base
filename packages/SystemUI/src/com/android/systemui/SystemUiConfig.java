/**
 * Manages carrier configs for systemui
 *
 * Add by Spreadtrum
 */
package com.android.systemui;

import android.content.Context;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;

public final class SystemUiConfig {
    private static SystemUiConfig sInstance;

    private Context mContext;
    private CarrierConfigManager mConfigManager;
    private PersistableBundle mPersistableBundle;

    /**
     * Get the singleton instance of SystemUiConfig
     */
    public synchronized static SystemUiConfig getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new SystemUiConfig(context);
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

    private SystemUiConfig(Context context) {
        mContext = context;
        mConfigManager = (CarrierConfigManager)mContext.getSystemService(
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
