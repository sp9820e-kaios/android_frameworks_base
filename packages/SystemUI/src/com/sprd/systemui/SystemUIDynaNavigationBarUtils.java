
package com.sprd.systemui;

import android.app.AddonManager;
import android.content.Context;
import com.android.systemui.R;
import com.android.systemui.statusbar.KeyguardAffordanceView;
import com.android.systemui.statusbar.phone.ActivityStarter;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import android.view.WindowManager;
import com.android.systemui.statusbar.KeyguardIndicationController;

public class SystemUIDynaNavigationBarUtils {
    static SystemUIDynaNavigationBarUtils sInstance;

    public SystemUIDynaNavigationBarUtils() {
    }

    public static SystemUIDynaNavigationBarUtils getInstance() {
        if (sInstance != null)
            return sInstance;
        sInstance = (SystemUIDynaNavigationBarUtils) AddonManager.getDefault().getAddon(
                R.string.feature_dynamic_navigationbat_systemui, SystemUIDynaNavigationBarUtils.class);
        return sInstance;
    }

    public static SystemUIDynaNavigationBarUtils getInstance(Context context) {
        if (sInstance != null)
            return sInstance;
        sInstance = (SystemUIDynaNavigationBarUtils) new AddonManager(context).getAddon(
                R.string.feature_dynamic_navigationbat_systemui, SystemUIDynaNavigationBarUtils.class);
        return sInstance;
    }

    public boolean isSupportDynaNaviBar() {
        return false;
    }
}
