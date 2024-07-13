
package com.sprd.systemui;

import android.app.AddonManager;
import android.content.Context;
import com.android.systemui.R;
import com.android.systemui.statusbar.KeyguardAffordanceView;
import com.android.systemui.statusbar.phone.ActivityStarter;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import android.view.WindowManager;
import com.android.systemui.statusbar.KeyguardIndicationController;

public class SystemUILockAppUtils {
    static SystemUILockAppUtils sInstance;

    public SystemUILockAppUtils() {
    }

    public static SystemUILockAppUtils getInstance() {
        if (sInstance != null) {
            return sInstance;
        }
        sInstance = (SystemUILockAppUtils) AddonManager.getDefault().getAddon(
                R.string.feature_lock_app_systemui, SystemUILockAppUtils.class);
        return sInstance;
    }

    public static SystemUILockAppUtils getInstance(Context context) {
        if (sInstance != null) {
            return sInstance;
        }
        sInstance = (SystemUILockAppUtils) new AddonManager(context).getAddon(
                R.string.feature_lock_app_systemui, SystemUILockAppUtils.class);
        return sInstance;
    }

    public boolean isSupportLockApp() {
        return false;
    }
}
