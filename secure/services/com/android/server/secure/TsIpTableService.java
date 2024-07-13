
package com.android.server.secure;

import static com.thundersoft.secure.TsIpTableManager.DEFAULT_CONNECT_STATE;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xmlpull.v1.XmlPullParserException;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.INetworkManagementService;
import android.util.AtomicFile;
import android.util.Slog;

import com.android.internal.util.XmlUtils;
import com.android.server.Watchdog;
import com.thundersoft.secure.TsIpTableManager;
import com.thundersoft.secure.ITsIpTableManager;
import com.google.android.collect.Maps;

public class TsIpTableService extends ITsIpTableManager.Stub {

    private static final String LOG_TAG = "TsIpTableService";
    private static final boolean DBG = true;

    private volatile boolean mSystemReady = false;

    private Context mContext;

    private TsIpTableCmd mCmd;

    private Map<String, Integer> mStatus;
    private AtomicFile mStateFile;
    private PackageManager mPm;

    /**
     * TsIpTableService
     *
     * @param context
     */
    @SuppressWarnings("unchecked")
    public TsIpTableService(Context context) {
        Slog.d(LOG_TAG, "init TsIpTableService");
        mContext = context;
        mCmd = TsIpTableCmd.getInstance();
    }

    public void ServiceReady() {
        mSystemReady = false;
        try {
            mPm = mContext.getPackageManager();
            initRules();
            List<String> currentApps = getNetApps(mContext);
            if (mStatus == null) {

                File stateFile = new File("/data/system/iptable_status.xml");
                if (stateFile.exists()) {
                    mStateFile = new AtomicFile(stateFile);
                    mStatus = (Map<String, Integer>) XmlUtils.readMapXml(mStateFile.openRead());
                    // init rules
                    List<String> pns = new ArrayList<String>(mStatus.keySet());
                    pns.remove(DEFAULT_CONNECT_STATE);
                    for (String packageName : pns) {
                        if (currentApps.contains(packageName)) {
                            applyRules(packageName, mStatus.get(packageName));
                        } else {
                            mStatus.remove(packageName);
                            Slog.d(LOG_TAG, "remove uninstall package:" + packageName);
                        }
                    }
                    writeStatusXml();
                } else {
                    Slog.d(LOG_TAG, "create iptable_status.xml");
                    stateFile.createNewFile();
                    mStateFile = new AtomicFile(stateFile);
                    mStatus = Maps.newHashMap();
                    for (String packageName : currentApps) {
                        setConnectStateOrg(packageName, getDefaultConnectStateOrg());
                    }
                }
            }
            mSystemReady = true;
        } catch (Exception e) {
            mSystemReady = false;
            Slog.e(LOG_TAG, "systemReady error:", e);
        }
    }

    /**
     * getConnectState
     *
     * @param packageName
     * @return 0 stop all; 1 3g only; 2 wifi only; 3 allow all
     */
    public int getConnectState(String packageName) throws RemoteException {
        Slog.d(LOG_TAG, "getConnectState mSystemReady = " + mSystemReady);
        if(!mSystemReady) {
            return TsIpTableManager.ALL_ALLOW;
        }

        if (mStatus.containsKey(packageName)) {
            return mStatus.get(packageName);
        }
        int value = TsIpTableManager.ALL_ALLOW;
        setConnectStateOrg(packageName, value);
        return value;
    }

    /**
     * setConnectState
     *
     * @param packageName
     * @param value : 0 stop all; 1 3g only; 2 wifi only; 3 allow all
     */
    public void setConnectState(String packageName, int value) throws RemoteException {
        Slog.d(LOG_TAG, "setConnectState mSystemReady = " + mSystemReady);
        if(!mSystemReady) {
            return;
        }

        setConnectStateOrg(packageName, value);
    }

    private void setConnectStateOrg(String packageName, int value) throws RemoteException {
      Slog.d(LOG_TAG, "setConnectStateOrg packageName=" + packageName + ", value=" + value);
      mStatus.put(packageName, value);
      if (!DEFAULT_CONNECT_STATE.equals(packageName)) {
          try {
              applyRules(packageName, value);
          } catch (Exception e) {
              Slog.e(LOG_TAG, "Exception:", e);
          }
      }
      writeStatusXml();
  }

    private boolean writeStatusXml() {
        FileOutputStream stream = null;
        boolean success = true;
        try {
            stream = mStateFile.startWrite();
            XmlUtils.writeMapXml(mStatus, stream);
        } catch (XmlPullParserException e) {
            Slog.e(LOG_TAG, "write iptable_status.xml XmlPullParserException:", e);
            success = false;
        } catch (Exception e) {
            Slog.e(LOG_TAG, "write iptable_status.xml Exception:", e);
            success = false;
        } finally {
            if (success) {
                mStateFile.finishWrite(stream);
            } else {
                mStateFile.failWrite(stream);
            }
        }
        return success;
    }

    /**
     * getDefaultConnectState
     *
     * @return 0 stop all; 1 3g only; 2 wifi only; 3 allow all
     */
    public int getDefaultConnectState() throws RemoteException {
        Slog.d(LOG_TAG, "getDefaultConnectState mSystemReady = " + mSystemReady);
        if(!mSystemReady) {
            return TsIpTableManager.ALL_ALLOW;
        }

        return getDefaultConnectStateOrg();
    }

    private int getDefaultConnectStateOrg() throws RemoteException {
        if (mStatus.containsKey(DEFAULT_CONNECT_STATE)) {
            return mStatus.get(DEFAULT_CONNECT_STATE);
        }
        // default allow all
        setConnectStateOrg(DEFAULT_CONNECT_STATE, TsIpTableManager.ALL_ALLOW);
        return TsIpTableManager.ALL_ALLOW;
    }

    /**
     * setDefaultConnectSetate
     *
     * @param value : 0 stop all; 1 3g only; 2 wifi only; 3 allow all
     */
    public void setDefaultConnectSetate(int value) throws RemoteException {
        if(!mSystemReady) {
            return;
        }

        setConnectStateOrg(DEFAULT_CONNECT_STATE, value);
    }

    private static final String INIT_RULES = "INITRULES";
    private static final String ACCEPT_DATA = "ACCEPT data ";
    private static final String REJECT_DATA = "REJECT data ";
    private static final String ACCEPT_WIFI = "ACCEPT wifi ";
    private static final String REJECT_WIFI = "REJECT wifi ";

    private INetworkManagementService mNMService;
    private INetworkManagementService getNetworkManagementService() {
        synchronized (this) {
            if (mNMService != null) {
                return mNMService;
            }

            IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
            mNMService = INetworkManagementService.Stub.asInterface(b);
            return mNMService;
        }
    }

    private boolean isBandwidthControlEnabled() {
        boolean isEnabled = false;
        try {
            isEnabled = getNetworkManagementService().isBandwidthControlEnabled();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return isEnabled;
    }

    private int handleCommand(String cmdStr) {
        while(true) {
            if(isBandwidthControlEnabled()) {
                Slog.d(LOG_TAG, "isBandwidthControlEnabled() is true");
                break;
            }
            Slog.d(LOG_TAG, "isBandwidthControlEnabled() is false");
            SystemClock.sleep(50);
        }

        return mCmd.handleCommand(cmdStr);
    }

    private void applyRules(String packageName, int value) throws NameNotFoundException {
        String command_3g = "";
        String command_wifi = "";
        int uid = mPm.getPackageUid(packageName, 0);
        Slog.d(LOG_TAG, "packageName=" + packageName + ", uid=" + uid);
        switch (value) {
            case TsIpTableManager.ALL_STOP:
                command_3g = REJECT_DATA + uid;
                command_wifi = REJECT_WIFI + uid;
                break;

            case TsIpTableManager.ALL_ALLOW:
                command_3g = ACCEPT_DATA + uid;
                command_wifi = ACCEPT_WIFI + uid;
                break;
            case TsIpTableManager.ONLY_DATA:
                command_3g = ACCEPT_DATA + uid;
                command_wifi = REJECT_WIFI + uid;
                break;
            case TsIpTableManager.ONLY_WIFI:
                command_3g = REJECT_DATA + uid;
                command_wifi = ACCEPT_WIFI + uid;
                break;
        }
        handleCommand(command_3g);
        handleCommand(command_wifi);
    }

    private void initRules() {
        Slog.d(LOG_TAG, "init iptables rules");
        handleCommand(INIT_RULES);
    }

    private List<String> getNetApps(Context context) {
        List<String> apps = new ArrayList<String>();
        List<String> packageNames = new ArrayList<String>();
        List<ApplicationInfo> list = new ArrayList<ApplicationInfo>();
        PackageManager pm = context.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(mainIntent, 0);
        for (ResolveInfo ri : ris) {
            String packageName = ri.activityInfo.applicationInfo.packageName;
            if (packageNames.contains(packageName)) {
                continue;
            }
            if (PackageManager.PERMISSION_GRANTED == pm.checkPermission(
                    Manifest.permission.INTERNET, packageName)) {
                apps.add(packageName);
            }
            packageNames.add(packageName);
        }
        return apps;
    }
}
