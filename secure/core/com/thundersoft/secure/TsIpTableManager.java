
package com.thundersoft.secure;

import java.util.HashMap;
import java.util.Set;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.thundersoft.secure.ITsIpTableManager;

/**
 * {@hide}
 */
public class TsIpTableManager {

    private static String TAG = "TsIpTableManager";
    public static String TSIPTABLE_SERVICE = "tsiptable";
    private final ITsIpTableManager mService;
    private final Context mContext;
    private static TsIpTableManager sInstance = null;

    public static final String DEFAULT_CONNECT_STATE = "ts_default_connect_state";
    public static final int ALL_STOP = 0;
    public static final int ONLY_DATA = 1;
    public static final int ONLY_WIFI = 2;
    public static final int ALL_ALLOW = 3;

    private static HandlerThread workThread;
    private static Handler handler;
    private static final int NORMAL = 1;
    private static final int DEFAULT = 2;

    /** @hide */
    public synchronized static TsIpTableManager get(Context context) {
        if (sInstance == null) {
            sInstance = (TsIpTableManager) context.getSystemService(TSIPTABLE_SERVICE);
        }
        return sInstance;
    }

    /** @hide */
    public TsIpTableManager(Context context, ITsIpTableManager service) {
        mService = service;
        mContext = context;
        workThread = new HandlerThread("IpTableManager_Work_Thread");
        workThread.start();
        handler = new Handler(workThread.getLooper()) {
            public void handleMessage(android.os.Message msg) {
                try {
                    Log.d("kings", "handleMessage what=" + msg.what);
                    switch (msg.what) {
                        case NORMAL:
                            mService.setConnectState(String.valueOf(msg.obj), msg.arg1);
                            break;
                        case DEFAULT:
                            mService.setDefaultConnectSetate(msg.arg1);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "error:", e);
                }
            };
        };
    }

    /**
     * ServiceReady
     */
    public void ServiceReady() {
        try {
            mService.ServiceReady();
        } catch (RemoteException re) {
            Log.e(TAG, "error:", re);
        }
    }

    /**
     * getConnectState
     *
     * @param packageName
     * @return 0 stop all ;1 3g only ;2 wifi only;3 allow all
     */
    public int getConnectState(String packageName) {
        try {
            return mService.getConnectState(packageName);
        } catch (RemoteException re) {
            Log.e(TAG, "error:", re);
            return -1;
        }
    }

    /**
     * setConnectState
     *
     * @param packageName
     * @param vaule : 0 stop all ;1 3g only ;2 wifi only;3 allow all
     */
    public void setConnectState(String packageName, int vaule) {
        try {
            Message msg = Message.obtain();
            msg.what = NORMAL;
            msg.obj = packageName;
            msg.arg1 = vaule;
            handler.sendMessage(msg);
            // mService.setConnectState(packageName, vaule);
        } catch (Exception re) {
            Log.e(TAG, "error:", re);
        }
    }

    /**
     * getDefaultConnectState
     *
     * @return 0 stop all ;1 3g only ;2 wifi only;3 allow all
     */
    public int getDefaultConnectState() {
        try {
            return mService.getDefaultConnectState();
        } catch (RemoteException re) {
            Log.e(TAG, "error:", re);
            return -1;
        }
    }

    /**
     * setDefaultConnectSetate
     *
     * @param vaule : 0 stop all ;1 3g only ;2 wifi only;3 allow all
     */
    public void setDefaultConnectSetate(int vaule) {
        try {
            Message msg = Message.obtain();
            msg.what = DEFAULT;
            msg.arg1 = vaule;
            handler.sendMessage(msg);
            // mService.setDefaultConnectSetate(vaule);
        } catch (Exception re) {
            Log.e(TAG, "error:", re);
        }
    }

    public static int getStateValue(boolean dataState, boolean wifiState) {
        int dataValue = dataState ? ONLY_DATA : 0;
        int wifiValue = wifiState ? ONLY_WIFI : 0;
        return dataValue + wifiValue;
    }

}
