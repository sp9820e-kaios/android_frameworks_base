package com.android.server.am;

import android.app.IActivityManager;
import android.app.ActivityManagerNative;
import android.content.Context;
import android.database.ContentObserver;
import android.hardware.input.InputManagerInternal;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Slog;

import com.android.server.LocalServices;

import java.util.ArrayList;
import java.util.List;

import static android.provider.Settings.Global.MOUSE_SUPPORT_LIST;


public class MouseObserverController implements ActivityFocusedListener {

    private static final String TAG = "MouseObserverController";
    private static final boolean DEBUG = true;
    private final Uri mMouseTouchUri = Settings.Global.getUriFor(MOUSE_SUPPORT_LIST);

    List<String> mPackageNameList = new ArrayList<String>();
    ActivityManagerService mService;
    InputManagerInternal mInputManagerInternal;
    SettingsDataObserver mObserver;

    MouseObserverController() {
        mService = (ActivityManagerService) ActivityManagerNative.getDefault();
        mInputManagerInternal = LocalServices.getService(InputManagerInternal.class);
        mObserver = new SettingsDataObserver(null);
        if(mService != null) {
            registerListeners();
        }
        queryPackageNameList();
    }

    private void registerListeners() {
        if(mMouseTouchUri != null) {
            mService.mContext.getContentResolver().registerContentObserver(mMouseTouchUri, false, mObserver);
            ActivityFocusedDispatcher.getDefault().registerFocusChangedListener(MouseObserverController.this);
        }
    }

    @Override
    public void onFocusedAppChanged(ActivityRecord r) {
        List<String> tmpPackageNameList = mPackageNameList;
        if(r != null) {
            if(tmpPackageNameList.size() > 0) {
                if (tmpPackageNameList.contains(r.appInfo.packageName)) {
                    enableMouseTouch();
                    if (DEBUG) Slog.d(TAG, "Application " + r.appInfo.packageName.toString() + " mouseTouch is enable now");
                } else {
                    disableMouseTouch();
                    //if (DEBUG)  Slog.d(TAG, "Application " + r.appInfo.packageName.toString() + " mouseTouch is disable");
                }
            } else {
                if (DEBUG) Slog.e(TAG, "Pls check current mPackageNameList is right now");
            }
        } else {
            if (DEBUG) Slog.e(TAG, "Pls check current ActivityRecord is right now");
        }
    }

    public void enableMouseTouch() {
        mInputManagerInternal.setEnableFor3rdApp(true);
    }

    public void disableMouseTouch() {
        mInputManagerInternal.setEnableFor3rdApp(false);
    }


    class SettingsDataObserver extends ContentObserver {

        SettingsDataObserver(Handler handler) {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (DEBUG) Slog.d(TAG, "Observer Uri:mMouseTouchUri data changed");
            queryPackageNameList();
        }

    }

    private void queryPackageNameList() {
        List<String> tmpPackageNameList = new ArrayList<String>();
        String data = Settings.Global.getString(mService.mContext.getContentResolver(),
                Settings.Global.MOUSE_SUPPORT_LIST);

        if(TextUtils.isEmpty(data)) {
            mPackageNameList = tmpPackageNameList;
            if (DEBUG) Slog.e(TAG, "Empty String data read from Settings.Global.MOUSE_SUPPORT_LIST");
            return;
        }

        if(!data.contains(",")) {
            mPackageNameList = tmpPackageNameList;
            if (DEBUG) Slog.e(TAG, "Malformed String data read from Settings.Global.MOUSE_SUPPORT_LIST");
            return;
        }

        String[] array = data.split(",");
        if(array != null && array.length > 0) {
            for(String str : array) {
                tmpPackageNameList.add(str);
            }
        } else {
            if (DEBUG) Slog.e(TAG, "Malformed String data split to array date, pls check data");
        }
        mPackageNameList = tmpPackageNameList;
    }

}
