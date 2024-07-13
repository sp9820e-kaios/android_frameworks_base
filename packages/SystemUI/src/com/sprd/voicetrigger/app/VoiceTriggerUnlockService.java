package com.sprd.voicetrigger.app;

import com.sprd.voicetrigger.aidl.IVoiceTriggerUnlock;
import com.android.keyguard.KeyguardUpdateMonitor;
import com.android.systemui.keyguard.KeyguardViewMediator;
import com.android.systemui.SystemUIApplication;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.util.Log;

public class VoiceTriggerUnlockService extends Service {
    private static final String TAG = "VoiceTriggerUnlockAIDLService";
    private KeyguardViewMediator mKeyguardViewMediator;
    private KeyguardUpdateMonitor mUpdateMonitor;
    private PowerManager mPM;

    IVoiceTriggerUnlock.Stub stub = new IVoiceTriggerUnlock.Stub() {

        @Override
        public boolean voiceTriggerUnlock() throws RemoteException {
            // TODO Auto-generated method stub
            wakeUpAndUnlock();
            Log.d(TAG, "voiceTriggerUnlock()");
            return true;
       }
    };

    public void wakeUpAndUnlock(){
        boolean isSimSceure = mUpdateMonitor.isSimPinSecure();
        Log.d(TAG,"wake lock   isSimSceure = "+isSimSceure);
        if(!isSimSceure){
            mKeyguardViewMediator.keyguardDone(false, false);
        }
        PowerManager.WakeLock wl = mPM.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_BRIGHT_WAKE_LOCK,"voice_trigger");
        wl.acquire();
        wl.release();
    }

    @Override
    public void onCreate() {
        // TODO Auto-generated method stub
        ((SystemUIApplication) getApplication()).startServicesIfNeeded();
        mKeyguardViewMediator =
                ((SystemUIApplication) getApplication()).getComponent(KeyguardViewMediator.class);
        mUpdateMonitor = KeyguardUpdateMonitor.getInstance(this);
        mPM = (PowerManager) getSystemService(Context.POWER_SERVICE);
        Log.d(TAG, "onCreate()");
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent arg0) {
        // TODO Auto-generated method stub
        Log.d(TAG, "onBind()");
        return stub;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // TODO Auto-generated method stub
        Log.d(TAG, "onStartCommand()");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // TODO Auto-generated method stub
        Log.d(TAG, "onUnbind()");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
    }

}
