/*opyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.am;

import android.app.ActivityManager;

import java.util.HashMap;
import java.util.HashSet;

//import android.app.IAlarmManager;
import android.app.IApplicationThread;
import android.os.ServiceManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.Intent;

import com.android.server.am.ActivityManagerService;

import android.os.Process;
import android.util.Log;
import android.util.Slog;
import android.util.SparseIntArray;
import android.os.SystemClock;

/**

 */
public final class ActivityManagerServiceEx extends ActivityManagerService {

    static final String TAG = "ActivityManagerEx";
    static final boolean DEBUG_AMSEX = false && !IS_USER_BUILD;
    // 15M, if the chached CHACH_PSS_MAX_VALUE , it adj will adjust to 8
    static final int CHACH_PSS_MAX_VALUE = 15 * 1025;
    boolean mIsInHome;
    private static final boolean sSupportCompatibityBroadcast = true;
    private final String[] mSpecialActions;
    private final String[] mBroadcastPutForeground;

    public ActivityManagerServiceEx(Context systemContext) {
        super(systemContext);
        mIsInHome = true;
        if(sSupportCompatibityBroadcast){
            BroadcastQueue[] broadcastQueues = new BroadcastQueue[4];
            broadcastQueues[0] = mBroadcastQueues[0];
            broadcastQueues[1] = mBroadcastQueues[1];
            broadcastQueues[2] = mCtsFgBroadcastQueue = new BroadcastQueue(this, this.mHandler, "foreground-comp", ActivityManagerService.BROADCAST_FG_TIMEOUT, false);
            broadcastQueues[3] = mCtsBgBroadcastQueue = new BroadcastQueue(this, this.mHandler, "background-comp", ActivityManagerService.BROADCAST_BG_TIMEOUT, false);
            mBroadcastQueues = broadcastQueues;
        }
        mSpecialActions = super.mContext.getResources().getStringArray(com.android.internal.R.array.special_broadcast_actions);
        mBroadcastPutForeground = super.mContext.getResources().getStringArray(com.android.internal.R.array.broadcast_put_foreground_actions);
    }

    boolean isTopHostApp(ProcessRecord app,  ProcessRecord top){
        if( top != null&& top != mHomeProcess&& app != mHomeProcess){
            for (int i = 0; i < top.activities.size(); i++){
               final ActivityRecord r = top.activities.get(i);
               if(r != null  && r.callerPid== app.pid){
                    return true;
               }
            }
        }
        return false;
   }

    /* AMS Optimization:3 */
    protected int computeOomAdjLocked(ProcessRecord app, int cachedAdj, ProcessRecord TOP_APP,
            boolean doingAll, long now) {
        mIsInHome = TOP_APP == mHomeProcess;
        int curadj = super.computeOomAdjLocked(app, cachedAdj, TOP_APP, doingAll, now);
        if (DEBUG_AMSEX)Slog.d(TAG, "computeOomAdjLocked enter app:" + app + " curadj:" + curadj);
        /* check State protection */
        switch (app.protectStatus) {
            case ActivityManager.PROCESS_STATUS_RUNNING:
            case ActivityManager.PROCESS_STATUS_MAINTAIN:
            case ActivityManager.PROCESS_STATUS_PERSISTENT: {
                int value = sProcProtectConfig.get(app.protectStatus);
                if(curadj > value) curadj = value;
                break;
            }
            case ActivityManager.PROCESS_PROTECT_CRITICAL:
            case ActivityManager.PROCESS_PROTECT_IMPORTANCE:
            case ActivityManager.PROCESS_PROTECT_NORMAL: {
                if (curadj >= app.protectMinAdj && curadj <= app.protectMaxAdj) {
                    int value = sProcProtectConfig.get(app.protectStatus);
                    if(curadj > value) curadj = value;
                }
                break;
            }
        }
        if(curadj > ProcessList.SERVICE_ADJ){
            if(app !=  TOP_APP && isTopHostApp(app, TOP_APP)){
                curadj = ProcessList.SERVICE_ADJ;
                app.cached = false;
                app.adjType = "host";
            }
        }
        if (DEBUG_AMSEX){
            Slog.d(TAG, "computeOomAdjLocked app.protectStatus:" + app.protectStatus + " app.protectMinAdj:" + app.protectMinAdj
                    + " protectMaxAdj:" + app.protectMaxAdj + " curadj:" + curadj);
        }
        if (curadj != app.curRawAdj) {
            // adj changed, adjust its other parameters:
            app.empty = false;
            app.cached = false;
            //app.keeping = true; 5.0--already removed
            app.adjType = "protected";
            app.curProcState = ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND;
            app.curSchedGroup = Process.THREAD_GROUP_DEFAULT;
            app.curAdj = curadj;
            if (DEBUG_AMSEX)Slog.d(TAG, "computeOomAdjLocked :" + app + " app.curAdj:" + app.curAdj);
        }
        return curadj;
    }

    /**
     * SPRD: getTotalCpuUsage
     * @hide
     */
    final public float getTotalCpuUsage() {
        synchronized (mProcessCpuTracker) {
            final long now = SystemClock.uptimeMillis();
            if (MONITOR_CPU_USAGE &&
                    mLastCpuTime.get() < (now - MONITOR_CPU_MIN_TIME)) {
                mLastCpuTime.set(now);
                mProcessCpuTracker.update();
            }
        }
        if (DEBUG_AMSEX) Slog.d(TAG, "getTotalCpuUsage :" + mProcessCpuTracker.getTotalCpuPercent());
        return mProcessCpuTracker.getTotalCpuPercent();
    }

    /**
     * SPRD: setProcessProtectStatus
     * @hide
     */
    public void setProcessProtectStatus(int pid, int status) {
        ProcessRecord app = null;
        synchronized (mPidsSelfLocked) {
            app = mPidsSelfLocked.get(pid);
        }
        if (app != null) {
            if (DEBUG_AMSEX) Slog.d(TAG, "setProcessProtectStatus, app: " + app + " status: " + status + " preStatus: " + app.protectStatus);
            synchronized (this) {
                app.protectStatus = status;
            }
        }
    }

    /**
     * SPRD: setProcessProtectStatus
     * @hide
     */
    public void setProcessProtectStatus(String appName, int status) {
        if (appName == null)  return;
        if (DEBUG_AMSEX) Slog.d(TAG, "setProcessProtectStatus :" + appName + " status:" + status);
        for (int i = mLruProcesses.size() - 1; i >= 0; i--) {
            ProcessRecord rec = mLruProcesses.get(i);
            if (rec != null && appName.equals(rec.processName)) {
                rec.protectStatus = status;
                if (DEBUG_AMSEX) Slog.d(TAG, "setProcessProtectStatus find app:" + rec);
                break;
            }
        }

    }

    /**
     * SPRD: setProcessProtectArea
     * @hide
     */
    public void setProcessProtectArea(String appName, int minAdj, int maxAdj, int protectLevel) {
        if (DEBUG_AMSEX){
            Slog.d(TAG, "setProcessProtectStatus :" + appName + " minAdj:" + minAdj + " maxAdj:"
                    + maxAdj + " protectLevel:" + protectLevel);
        }
        if (appName == null) return;
        synchronized (mPidsSelfLocked) {
            for (int i = mLruProcesses.size() - 1; i >= 0; i--) {
                ProcessRecord rec = mPidsSelfLocked.valueAt(i);
                if (rec != null && appName.equals(rec.processName)) {
                    rec.protectStatus = protectLevel;
                    rec.protectMinAdj = minAdj;
                    rec.protectMaxAdj = maxAdj;
                    if (DEBUG_AMSEX) Slog.d(TAG, "setProcessProtectArea find app:" + rec);
                    break;
                }
            }
        }
    }

    public static class ProtectArea
    {
        int mMinAdj;
        int mMaxAdj;
        int mLevel;

        public ProtectArea(int minAdj, int maxAdj, int protectLevel)
        {
            mMinAdj = minAdj;
            mMaxAdj = maxAdj;
            mLevel = protectLevel;
        }

        @Override
        public String toString() {
            return "ProtectArea [mMinAdj=" + mMinAdj + ", mMaxAdj=" + mMaxAdj + ", mLevel="
                    + mLevel + "]";
        }
    }

    static HashMap<String, ProtectArea> sPreProtectAreaList;
    static HashSet<String> sHasAlarmList;
    static HashSet<String> sNeedCleanPackages;
    static SparseIntArray sProcProtectConfig;

    static {
        sPreProtectAreaList = new HashMap<String, ProtectArea>();

        sHasAlarmList = new HashSet<String>();
        sNeedCleanPackages = new HashSet<String>();

        sProcProtectConfig = new SparseIntArray();
        sProcProtectConfig.put(ActivityManager.PROCESS_STATUS_RUNNING, 0);
        sProcProtectConfig.put(ActivityManager.PROCESS_STATUS_MAINTAIN, 2);
        sProcProtectConfig.put(ActivityManager.PROCESS_STATUS_PERSISTENT, -12);
        sProcProtectConfig.put(ActivityManager.PROCESS_PROTECT_CRITICAL, 0);
        sProcProtectConfig.put(ActivityManager.PROCESS_PROTECT_IMPORTANCE, 2);
        sProcProtectConfig.put(ActivityManager.PROCESS_PROTECT_NORMAL, 4);
    }

    protected void startProcessLocked(ProcessRecord app,
            String hostingType, String hostingNameStr) {
        super.startProcessLocked(app, hostingType, hostingNameStr);
        if (app.processName != null) {
            ProtectArea pa = sPreProtectAreaList.get(app.processName);
            if (pa != null) {
                app.protectStatus = pa.mLevel;
                app.protectMinAdj = pa.mMinAdj;
                app.protectMaxAdj = pa.mMaxAdj;
            }
            if (DEBUG_AMSEX){
                Slog.d(TAG, "startProcessLocked app.protectLevel :" + app.protectStatus
                        + " app.protectMinAdj :" + app.protectMinAdj
                        + " app.protectMaxAdj" + app.protectMaxAdj);
            }
        }
    }
//   boolean cleanUpApplicationRecordLocked(ProcessRecord app,
//         boolean restarting, boolean allowRestart, int index) {
//         if(app != null && app.info != null && needCleanPackages.contains(app.info.packageName)){
//             mFgBroadcastQueue.cleanDiedAppBroadcastLocked(app);
//             mBgBroadcastQueue.cleanDiedAppBroadcastLocked(app);
//         }
//             super.cleanUpApplicationRecordLocked(app, restarting, allowRestart,index);
//   }

//    void appDiedLocked(ProcessRecord app, int pid,
//            IApplicationThread thread) {
//        if (hasAlarmList.contains(app.processName)
//         ||(app.info !=null && needCleanPackages.contains(app.info.packageName))) {
//            ApplicationInfo diedAppInfo = app.instrumentationInfo != null
//                    ? app.instrumentationInfo : app.info;
//            final String roguePack = new String(diedAppInfo.packageName);
//            mHandler.post(new Runnable() {
//                public void run() {
//                    try {
//                        IAlarmManager alarmService = IAlarmManager.Stub.asInterface(ServiceManager
//                                .getService("alarm"));
//                        if (alarmService != null
//                                && alarmService.checkAlarmForPackageName(roguePack)) {
//                            alarmService.removeAlarmForPackageName(roguePack);
//                            if (DEBUG_AMSEX)
//                                Slog.w(TAG, "RemoveSvcAlarm: pkg=" + roguePack);
//                        }
//                    } catch (Exception e) {
//                        if (DEBUG_AMSEX)
//                            Slog.e(TAG, "RemoveSvcAlarm: Error!! " + e);
//                    }
//                }
//            });
//        }
//        super.appDiedLocked(app, pid, thread);
//    }

    public void addProtectArea(final String processName, final ProtectArea area) {
        if(processName == null || area == null) {
            return;
        }
        if (DEBUG_AMSEX) Slog.d(TAG, "addProtectArea, processName: " + processName + " ProtectArea: " + area);
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                synchronized (ActivityManagerServiceEx.this) {
                    sPreProtectAreaList.put(processName, area);
                    updateOomAdjLocked();
                }
            }

        });
    }

    public void removeProtectArea(final String processName) {
        if(processName == null) {
            return;
        }
        sPreProtectAreaList.remove(processName);
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                synchronized (ActivityManagerServiceEx.this) {
                    ProcessRecord app = null;
                    for(int i = mLruProcesses.size() -1; i >= 0; i--) {
                        if(processName.equals(mLruProcesses.get(i).processName)) {
                            app = mLruProcesses.get(i);
                            break;
                        }
                    }
                    if (DEBUG_AMSEX) Slog.d(TAG, "removeProtectArea, processName: " + processName + " app: " + app);
                    if(app != null) {
                        updateOomAdjLocked(app);
                    }
                }
            }

        });
    }

    boolean isPendingBroadcastProcessLocked(int pid) {
        if(!sSupportCompatibityBroadcast){
            return super.isPendingBroadcastProcessLocked(pid);
        }
        return super.isPendingBroadcastProcessLocked(pid)
                || mCtsFgBroadcastQueue.isPendingBroadcastProcessLocked(pid)
                || mCtsBgBroadcastQueue.isPendingBroadcastProcessLocked(pid);
    }

    public BroadcastQueue broadcastQueueForIntent(String callerPackage, Intent intent) {
        if(!sSupportCompatibityBroadcast){
            return broadcastQueueForIntent(intent);
        }
        final boolean isCtsPkg = isCtsPackage(callerPackage) || isSpecialAction(intent);
        final boolean isFg = isPutForeground(intent) || ((intent.getFlags() & Intent.FLAG_RECEIVER_FOREGROUND) != 0);
        if (ActivityManagerDebugConfig.DEBUG_BROADCAST_BACKGROUND && isCtsPkg) {
            Slog.i(TAG, "Check broadcast intent " + intent + " on "
                    + (isFg ? (isCtsPkg ? "foreground-comp" : "foreground") : (isCtsPkg ? "background-comp" : "background")) + " queue");
        }
        if(isFg == true && isCtsPkg == false){
            return mBroadcastQueues[0];
        }else if(isFg == false && isCtsPkg == false){
            return mBroadcastQueues[1];
        }else if(isFg == true && isCtsPkg == true){
            return mBroadcastQueues[2];
        }else{
            return mBroadcastQueues[3];
        }
    }

    private boolean isCtsPackage(String pkg) {
        if(!sSupportCompatibityBroadcast){
            return false;
        }
        if (pkg != null && pkg.contains("com.android.cts")) {
            return true;
        }
        return false;
    }

    private boolean isPutForeground(Intent intent){
        if(intent.getAction() == null || mBroadcastPutForeground.length <= 0) return false;
        for (int i = 0; i < mBroadcastPutForeground.length; i++) {
            if(intent.getAction().equals(mBroadcastPutForeground[i])) {
                Slog.d(TAG,"put to foreground broadcast queue : "+intent.getAction());
                intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                return true;
            }
        }
        return false;
    }

    private boolean isSpecialAction(Intent intent){
        if(intent.getAction() == null || mSpecialActions.length <= 0) return false;
        for (int i = 0; i < mSpecialActions.length; i++) {
            if(intent.getAction().equals(mSpecialActions[i])) {
                Slog.d(TAG,"special broadcast add to cts broadcast list : "+intent.getAction());
                return true;
            }
        }
        return false;
    }

    public BroadcastQueue[] getFgOrBgQueues(int flags) {
        if(!sSupportCompatibityBroadcast){
            return super.getFgOrBgQueues(flags);
        }
        boolean foreground = (flags & Intent.FLAG_RECEIVER_FOREGROUND) != 0;
        return foreground ? new BroadcastQueue[] { mFgBroadcastQueue, mCtsFgBroadcastQueue } : new BroadcastQueue[] { mBgBroadcastQueue, mCtsBgBroadcastQueue };
    }
}



