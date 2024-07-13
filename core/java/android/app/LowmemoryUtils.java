package android.app;

import android.os.RemoteException;

/**
 *  this file is for Lowmemory case.
 *  Provide api for application.
 *  Its function is likely SPRD_ActivityManager
 *
 *  @hide
 */

public class LowmemoryUtils {

    // SPRD: 380668 add kill-stop process @{
    public static final int KILL_STOP_FRONT_APP = 1;

    public static final int KILL_CONT_STOPPED_APP = 0;

    public static final int CANCEL_KILL_STOP_TIMEOUT = 2;

    public static void killStopFrontApp(int func) {
        try {
            ActivityManagerNative.getDefault().killStopFrontApp(func);
        } catch (RemoteException e) {
            // System dead, we will be dead too soon!
        }
    }
    //@}
}

