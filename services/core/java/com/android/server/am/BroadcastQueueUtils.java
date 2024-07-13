/*
 * Copyright © 2016 Spreadtrum Communications Inc.
 */

package com.android.server.am;

import android.util.Slog;
import android.app.AppOpsManager;
import android.content.pm.ResolveInfo;

/**
 * @hide
 */
public class BroadcastQueueUtils {

    private static final String BASETAG = "BroadcastQueueUtils";

    public static boolean stop3rdApp(ResolveInfo info, BroadcastRecord r) {
        boolean enabled = (r.queue.mService.mAppOpsService.checkOperation(
                AppOpsManager.OP_POST_AUTORUN, info.activityInfo.applicationInfo.uid,
                info.activityInfo.packageName) == AppOpsManager.MODE_ALLOWED);
        if (!enabled) {
            Slog.w(BASETAG, "avoid 3rd_app auto start : " + info.activityInfo.processName
                    + " for broadcast " + r.intent.getAction());
            r.queue.logBroadcastReceiverDiscardLocked(r);
            r.queue.finishReceiverLocked(r, r.resultCode, r.resultData, r.resultExtras,
                    r.resultAbort, true);
            r.queue.scheduleBroadcastsLocked();
            r.state = BroadcastRecord.IDLE;
        }
        return !enabled;
    }

}

