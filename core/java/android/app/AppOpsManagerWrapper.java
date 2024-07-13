/*
 * Copyright © 2016 Spreadtrum Communications Inc.
 */

package android.app;

import android.app.AppOpsManager;

/**
 * @hide
 */
public class AppOpsManagerWrapper {
    public static final int OP_POST_AUTORUN = AppOpsManager.OP_POST_AUTORUN;

    static int[] addItem(int[] origin, int added){
        int[] wanted = new int[origin.length + 1];
        System.arraycopy(origin, 0, wanted, 0, origin.length);
        wanted[wanted.length - 1] = added;
        return wanted;
    }

    static boolean[] addItem(boolean[] origin, boolean added){
        boolean[] wanted = new boolean[origin.length + 1];
        System.arraycopy(origin, 0, wanted, 0, origin.length);
        wanted[wanted.length - 1] = added;
        return wanted;
    }

    static String[] addItem(String[] origin, String added){
        String[] wanted = new String[origin.length + 1];
        System.arraycopy(origin, 0, wanted, 0, origin.length);
        wanted[wanted.length - 1] = added;
        return wanted;
    }
}
