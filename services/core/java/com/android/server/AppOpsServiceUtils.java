/*
 * Copyright © 2016 Spreadtrum Communications Inc.
 */

package com.android.server;

import android.app.AppOpsManager;
import android.content.Context;
import android.os.Process;

/**
 * @hide
 */
public class AppOpsServiceUtils {
    public static int changeCurUidOrNot(int opsUid, String opsPackageName, int curUid, Context context) {
        if (opsUid >= Process.FIRST_APPLICATION_UID && curUid == -1) {
            String name = context.getPackageManager().getNameForUid(opsUid);
            if (opsPackageName.equals(name)) {
                curUid = opsUid;
            }
        }
        return curUid;
    }
}

