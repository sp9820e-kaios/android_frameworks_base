package com.android.providers.settings;

import java.util.ArrayList;
import java.util.HashMap;
import android.content.ComponentName;
import android.util.Log;
import android.app.AddonManager;
import android.app.Activity;
import android.content.Context;

public class SprdCmccWorkspaceAddonStub {
    private static final String TAG = "SprdCmccWorkspaceAddonStub";
    private static SprdCmccWorkspaceAddonStub sInstance;
    protected boolean mHasCustomizeData = false;

    public static SprdCmccWorkspaceAddonStub getInstance(Context context ) {
        if (sInstance == null) {
            AddonManager addonManager = new AddonManager(context);
            sInstance = (SprdCmccWorkspaceAddonStub) addonManager.getAddon(R.string.feature_cmcc_app, SprdCmccWorkspaceAddonStub.class);
        }
        return sInstance;
    }

    protected boolean isDefault() {
        Log.d(TAG, "[isDefault]::");
        return mHasCustomizeData;
    }
}
