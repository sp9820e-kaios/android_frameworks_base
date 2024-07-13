package com.sprd.systemui;

import android.app.AddonManager;
import android.content.Context;

import com.android.systemui.R;
import com.android.systemui.statusbar.KeyguardAffordanceView;
import com.android.systemui.statusbar.phone.ActivityStarter;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import android.view.WindowManager;
import com.android.systemui.statusbar.KeyguardIndicationController;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

public class SprdLockScreenStub {
    private static SprdLockScreenStub sInstance;
    public static final String LOG_TAG = "SprdLockScreenStub";
    private final boolean isWallPaperLockScreenEnabled = false;

    public static SprdLockScreenStub getInstance() {
        if (sInstance == null) {
            sInstance = (SprdLockScreenStub) AddonManager.getDefault().getAddon(
                    R.string.feature_lock_screen, SprdLockScreenStub.class);
            if (sInstance == null) {
                sInstance = new SprdLockScreenStub();
            }
        }
        return sInstance;
    }

    public boolean isEnabled() {
        return isWallPaperLockScreenEnabled;
    }

    public boolean launchAudioProfile(ActivityStarter activityStarter, Context context) {
        return false;
    }

    public void changeCameraToProfile(KeyguardAffordanceView keyguardAffordanceView) {
    }

    public void changeHeadsUpbelowStatusBar(WindowManager.LayoutParams lp, PhoneStatusBar bar) {
    }

    public boolean changeProfileHint(KeyguardIndicationController keyguardIndicationController) {
        return false;
    }
}
