/*copyright (C) 2006 The Android Open Source Project
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

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import android.app.ActivityManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemProperties;
import android.os.SystemClock;
import android.util.EventLog;
import android.util.Slog;

import com.android.internal.app.ProcessStats;
import com.android.server.am.ActiveServices;

/**
 * Implement the restart of service in backgroud
 * @author SPRD
 */
public final class ActiveServicesEx extends ActiveServices {
    static final String TAG = "ActiveServicesEx";
    // 30%, if cup usage <= 30%, some died app can be restarted
    static final int CPU_IDLE_PRECENT_LOW = 30;
    static final int BALANCE_APP_MEMORY = 20 * 1024; //20M

    final ActivityManagerServiceEx mAmEx;
    //unit is KB
    private long mFreeMemory;
    private long mLastReadMemTime;

    static final boolean DEBUG_AMSEX = false;

    //service list which restart in backgroud
    final ArrayList<ServiceRecord> mBgRestartServices = new ArrayList<ServiceRecord>();

    HashMap<String, Integer> mProtectBGruningApps;

    private void initProtectBGruningApps() {
        mProtectBGruningApps = new HashMap<String, Integer>();
        Resources res = mAm.mContext.getResources();
        int restartServiceInterval = res.getInteger(com.android.internal.R.integer.config_restartServiceInterval);
        for(String pkgName: res.getStringArray(com.android.internal.R.array.restartServiceInterval)) {
            mProtectBGruningApps.put(pkgName, restartServiceInterval);
        }
        int restartAppServiceInterval = res.getInteger(com.android.internal.R.integer.config_restartAppServiceInterval);
        for(String pkgName: res.getStringArray(com.android.internal.R.array.restartAppServiceInterval)) {
            mProtectBGruningApps.put(pkgName, restartAppServiceInterval);
        }
        if(DEBUG_AMSEX) {
            Slog.d(TAG, "initProtectBGruningApps restartServiceInterval: " + restartServiceInterval
                    + " restartAppServiceInterval: " + restartAppServiceInterval);
        }
    }

    public ActiveServicesEx(ActivityManagerService service) {
        super(service);
        mAmEx = (ActivityManagerServiceEx)service;
        mFreeMemory = 0;
        mLastReadMemTime = 0;
     };

    private void updateFreeMemory() {
        if (mFreeMemory == 0 || mLastReadMemTime + 5 * 1000 < SystemClock.uptimeMillis()) {
            mFreeMemory = Process.getAvailMemory() / 1024;
            mLastReadMemTime = SystemClock.uptimeMillis();
        }
        if (DEBUG_AMSEX) Slog.d(TAG, "updateFreeMemory: " + mFreeMemory);
    }

    //app's pss < free memory
    private boolean isAppPssLessFreeMemory(ServiceRecord srv) {
        long pss = 0;
        if (srv != null && srv.app != null)
            pss = srv.app.lastPss + BALANCE_APP_MEMORY;
        updateFreeMemory();
        if (DEBUG_AMSEX) {
            Slog.d(TAG, "isAppPssLessFreeMemory freeMem:" + mFreeMemory + "  pss:" + pss);
        }
        return pss <= mFreeMemory;
    }

    //cpu usage <= 30%
    private boolean isCurrentCpuIdle() {
        return (mAmEx.getTotalCpuUsage() <= CPU_IDLE_PRECENT_LOW);
    }

    protected void performServiceRestartLocked(ServiceRecord r) {
        if(r == null) {
            return;
        }
        boolean canStartBg = false;
        if (DEBUG_AMSEX){
            Slog.d(TAG, "performServiceRestartLocked :" + r + " mPendingServices.size():" + mPendingServices.size());
        }
        if (isServiceProcessRunningLocked(r) || isSameProcessPending(r)) {
            // if this service's app already started, bringup this service right now
            canStartBg = true;
        } else if((r.appInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_PERSISTENT))
                == (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_PERSISTENT)) {
            canStartBg = true;
        } else if(isAppPssLessFreeMemory(r) && (mAmEx.mIsInHome || isCurrentCpuIdle())) {
            canStartBg = true;
        }
        // restarting ..
        if (canStartBg) {
            super.performServiceRestartLocked(r);
            mBgRestartServices.remove(r);
        } else if (!mBgRestartServices.contains(r)) {
            mBgRestartServices.add(r);
        }

        if(!mBgRestartServices.isEmpty()) {
            ServiceRecord restartService = mBgRestartServices.get(0);
            if(DEBUG_AMSEX) {
                Slog.d(TAG, "performServiceRestartLocked mBgRestartServices.size: " + mBgRestartServices.size() +
                        " restartService: " + restartService);
            }
            mAmEx.mHandler.removeCallbacks(restartService.restarter);
            int dealyedTime = 0;
            if(mProtectBGruningApps == null){
                initProtectBGruningApps();
            }
            if(mProtectBGruningApps.containsKey(restartService.processName)) {
                dealyedTime = mProtectBGruningApps.get(restartService.processName);
            }
            if(dealyedTime < ActiveServices.SERVICE_MIN_RESTART_TIME_BETWEEN) {
                dealyedTime = ActiveServices.SERVICE_MIN_RESTART_TIME_BETWEEN;
            }
            mAmEx.mHandler.postDelayed(restartService.restarter, dealyedTime);
        }
    }

    final boolean isSameProcessPending(ServiceRecord r) {
        boolean isSame = false;
        if (r != null && r.processName != null) {
            for (int i = mPendingServices.size() - 1; i >= 0; i--) {
                ServiceRecord pr = mPendingServices.get(i);
                if (pr != null && r.processName.equals(pr.processName)) {
                    isSame = true;
                    break;
                }
            }
        }
        if (DEBUG_AMSEX) Slog.d(TAG, "isSameProcessPending r:" + r + " isSame:" + isSame);
        return isSame;
    }

    final boolean isServiceProcessRunningLocked(ServiceRecord r) {
        if (r == null)
            return false;
        boolean isolated = (r.serviceInfo.flags & ServiceInfo.FLAG_ISOLATED_PROCESS) != 0;
        ProcessRecord app;
        if (!isolated) {
            app = mAm.getProcessRecordLocked(r.processName, r.appInfo.uid, false);
        } else {
            app = r.isolatedProc;
        }
        boolean isRunning = (app != null && app.thread != null);
        if (DEBUG_AMSEX) {
            Slog.d(TAG, "isServiceProcessRunningLocked r:" + r + " isRunning:" + isRunning);
        }
        return isRunning;
    }

    protected void killServicesLocked(ProcessRecord app, boolean allowRestart) {
        // Report disconnected services.
         if(DEBUG_AMSEX)Slog.d(TAG, "killServicesLocked enter");
        if (false) {
            // XXX we are letting the client link to the service for
            // death notifications.
            if (app.services.size() > 0) {
                Iterator<ServiceRecord> it = app.services.iterator();
                while (it.hasNext()) {
                    ServiceRecord r = it.next();
                    for (int conni=r.connections.size()-1; conni>=0; conni--) {
                        ArrayList<ConnectionRecord> cl = r.connections.valueAt(conni);
                        for (int i=0; i<cl.size(); i++) {
                            ConnectionRecord c = cl.get(i);
                            if (c.binding.client != app) {
                                try {
                                    //c.conn.connected(r.className, null);
                                } catch (Exception e) {
                                    // todo: this should be asynchronous!
                                    Slog.w(TAG, "Exception thrown disconnected servce "
                                          + r.shortName
                                          + " from app " + app.processName, e);
                                }
                            }
                        }
                    }
                }
            }
        }
         // First clear app state from services.

        for (int i=app.services.size()-1; i>=0; i--) {
        ServiceRecord sr = app.services.valueAt(i);
        synchronized (sr.stats.getBatteryStats()) {
            sr.stats.stopLaunchedLocked();
        }
        /*if (sr.app != null && !sr.app.persistent) {
            sr.app.services.remove(sr);
        }
        */
        sr.callingApps.remove(app);
        if(mProtectBGruningApps == null) {
            initProtectBGruningApps();
        }
        int systemPersistentFlag = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_PERSISTENT;
        boolean canRestart = (sr.appInfo.flags & systemPersistentFlag) == systemPersistentFlag || mProtectBGruningApps.containsKey(sr.processName);
        if(!canRestart) {
            canRestart = !sr.stopIfKilled && ((ApplicationInfo.FLAG_SYSTEM & sr.appInfo.flags) != 0);
            if(!canRestart) {
                app.services.remove(sr);
                if(DEBUG_AMSEX) {
                    Slog.d(TAG, "killServicesLocked remove service: " + sr);
                }
            }
        }
        sr.app = null;
        sr.isolatedProc = null;
        sr.executeNesting = 0;
        sr.forceClearTracker();
        /* SPRD: cancel notification when app die @{ */
        if (sr.foregroundNoti != null ) {
            sr.cancelNotification();
            sr.foregroundId = 0;
            sr.foregroundNoti = null;
        }
        /* @} */
        if (mDestroyingServices.remove(sr)) {
            if (DEBUG_SERVICE) Slog.v(TAG, "killServices remove destroying " + sr);
        }

        final int numClients = sr.bindings.size();
        for (int bindingi=numClients-1; bindingi>=0; bindingi--) {
            IntentBindRecord b = sr.bindings.valueAt(bindingi);
            if (DEBUG_SERVICE) Slog.v(TAG, "Killing binding " + b
                    + ": shouldUnbind=" + b.hasBound);
            b.binder = null;
            b.requested = b.received = b.hasBound = false;
        }

        }
        // Clean up any connections this application has to other services.
        for (int i=app.connections.size()-1; i>=0; i--) {
            ConnectionRecord r = app.connections.valueAt(i);
            removeConnectionLocked(r, app, null);
        }
        app.connections.clear();

        ServiceMap smap = getServiceMap(app.userId);

        // Now do remaining service cleanup.
        for (int i=app.services.size()-1; i>=0; i--) {
            ServiceRecord sr = app.services.valueAt(i);
            // Sanity check: if the service listed for the app is not one
            // we actually are maintaining, drop it.
            if (smap.mServicesByName.get(sr.name) != sr) {
                ServiceRecord cur = smap.mServicesByName.get(sr.name);
                Slog.d(TAG, "Service " + sr + " in process " + app
                        + " not same as in map: " + cur);
                app.services.removeAt(i);
                continue;
            }

            // Any services running in the application may need to be placed
            // back in the pending list.
            if (allowRestart && sr.crashCount >= 2 && (sr.serviceInfo.applicationInfo.flags
                    &ApplicationInfo.FLAG_PERSISTENT) == 0) {
                Slog.w(TAG, "Service crashed " + sr.crashCount
                        + " times, stopping: " + sr);
                EventLog.writeEvent(EventLogTags.AM_SERVICE_CRASHED_TOO_MUCH,
                        sr.userId, sr.crashCount, sr.shortName, app.pid);
                bringDownServiceLocked(sr);
            } else if (!allowRestart) {
                bringDownServiceLocked(sr);
            } else {
                boolean canceled = scheduleServiceRestartLocked(sr, true);

                // Should the service remain running?  Note that in the
                // extreme case of so many attempts to deliver a command
                // that it failed we also will stop it here.
                if (sr.startRequested && (sr.stopIfKilled || canceled)) {
                    if (sr.pendingStarts.size() == 0) {
                        sr.startRequested = false;
                        if (sr.tracker != null) {
                            sr.tracker.setStarted(false, mAm.mProcessStats.getMemFactorLocked(),
                                    SystemClock.uptimeMillis());
                        }
                        if (!sr.hasAutoCreateConnections()) {
                            // Whoops, no reason to restart!
                            bringDownServiceLocked(sr);
                        }
                    }
                }
            }
        }

        if (!allowRestart) {
            app.services.clear();

            // Make sure there are no more restarting services for this process.
            for (int i=mRestartingServices.size()-1; i>=0; i--) {
                ServiceRecord r = mRestartingServices.get(i);
                if (r.processName.equals(app.processName) &&
                        r.serviceInfo.applicationInfo.uid == app.info.uid) {
                    mRestartingServices.remove(i);
                    clearRestartingIfNeededLocked(r);
                }
            }
            for (int i=mPendingServices.size()-1; i>=0; i--) {
                ServiceRecord r = mPendingServices.get(i);
                if (r.processName.equals(app.processName) &&
                        r.serviceInfo.applicationInfo.uid == app.info.uid) {
                    mPendingServices.remove(i);
                }
            }
        }

        // Make sure we have no more records on the stopping list.
        int i = mDestroyingServices.size();
        while (i > 0) {
            i--;
            ServiceRecord sr = mDestroyingServices.get(i);
            if (sr.app == app) {
                sr.forceClearTracker();
                mDestroyingServices.remove(i);
                if (DEBUG_SERVICE) Slog.v(TAG, "killServices remove destroying " + sr);
            }
        }
        app.executingServices.clear();
    }
}

