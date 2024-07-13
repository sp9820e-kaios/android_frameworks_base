/*
 * The Spreadtrum Communication 2016.
 */
package com.android.server.am;

import android.app.ActivityManagerNative;
import android.content.Context;
import android.util.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ActivityFocusedDispatcher {
    private static final String TAG = "ActivityFocusedDispatcher";

    private static Singleton<ActivityFocusedDispatcher> sDefault =
            new Singleton<ActivityFocusedDispatcher>() {

        @Override
        public ActivityFocusedDispatcher create() {
            return new ActivityFocusedDispatcher();
        }
    };

    public static ActivityFocusedDispatcher getDefault() {
        return sDefault.get();
    }

    private final List<ActivityFocusedListener> mListeners = new CopyOnWriteArrayList<>();
    private ActivityManagerService mService;

    // Do not construct this one.
    private ActivityFocusedDispatcher() {
        mService = (ActivityManagerService) ActivityManagerNative.getDefault();
    }

    public void registerFocusChangedListener(ActivityFocusedListener l) {
        int index = mListeners.indexOf(l);
        if (index >= 0) {
            mListeners.set(index, l);
        } else {
            mListeners.add(l);
        }
    }

    public void unregisterFocusChangedListener(ActivityFocusedListener l) {
        mListeners.remove(l);
    }

    public void notifyFocusChanged(ActivityRecord r) {
        if (!mListeners.isEmpty()) {

            final ActivityRecord recordFinal = r;
            for (ActivityFocusedListener l : mListeners) {
                if (null != l) {
                    l.onFocusedAppChanged(recordFinal);
                }
            }

        }
    }

}
