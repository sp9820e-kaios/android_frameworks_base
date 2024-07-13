
package android.hardware.boardScore;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Binder;
import android.os.SystemProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import android.os.RemoteException;
import android.util.Slog;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;

import java.io.FileOutputStream;
import java.io.IOException;

public class BoardScoreService extends IBoardScoreService.Stub {

    private static final String TAG = "BoardScore";
    private ScoreThread mScoreworkingThread;
    private ThermalInterface mThermal;
    private Context mContext;
    List<String> softwareList = new ArrayList<String>();


    public BoardScoreService(Context context) {
        mContext = context;
        getSoftwareList();
        mScoreworkingThread = new ScoreThread();
        mThermal = new ThermalInterface();
        new Thread(mScoreworkingThread).start();

    }

    public void StartBoardScoreService() {
        return;
    }

    public void getSoftwareList() {
        softwareList.add("antutu");
        softwareList.add("benchmark");
        softwareList.add("ludashi");
        softwareList.add("cfbench");
        softwareList.add("quicinc.vellamo");
        softwareList.add("geekbench");
        softwareList.add("greenecomputing.linpack");
        softwareList.add("nenamark");
        softwareList.add("performance.test");
        softwareList.add("Quadrant");
        softwareList.add("farproc.wifi.analyzer");
        
        softwareList.add("qihoo360.mobilesafe.opti");
        softwareList.add("eembc.coremark");
        softwareList.add("rightware.tdmm2v10jnifree");
        softwareList.add("tactel.electopia");
        softwareList.add("mobilebench.mb");
        softwareList.add("aurorasoftworks.quadrant");
        softwareList.add("futuremark.dmandroid");

        softwareList.add("andromeda.androbench");
        softwareList.add("rightware.Basemark");
        softwareList.add("smartbench.twelve");
        softwareList.add("tptest");
        softwareList.add("mobilexprt");
        softwareList.add("Benchmark");

    }

    public void setSoftwareList(List<String> softwareList) {
        this.softwareList = softwareList;
    }

    class ScoreThread implements Runnable {
        private String psname;
        // speed up: bSPeeding = true
        private boolean bSpeeding = false;
        // find a board score softeware in runing process
        private boolean beFind = false;

        public ScoreThread() {

        }

        public void run() {
            do {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                }
                ActivityManager _ActivityManager = (ActivityManager) mContext
                        .getSystemService(Context.ACTIVITY_SERVICE);
                List<RunningTaskInfo> list = _ActivityManager.getRunningTasks(1);
                if(list.size()<=0){
			continue;
			}
                beFind = false;
                for (int j = 0; j < list.size(); j++) {
                    psname = list.get(j).baseActivity.toString();
                    for (int n = 0; n < softwareList.size(); n++) {
                        String pkname = softwareList.get(n);
                        if (psname.contains(pkname)) {
                            beFind = true;
                            break;
                        }
                    }
                    if (beFind) {
                        break;
                    }
                }
                if (beFind) {
                    if (!bSpeeding) {
                        Slog.i(TAG, "Need to speed up!");
                        //SystemProperties.set("ctl.start", "inputfreq");
                        //Slog.i(TAG,"speed up ok!");
                        mThermal.thermalEnabled(false);
                        bSpeeding = true;
                    }
                }
                else
                {
                    if (bSpeeding) {
                        Slog.i(TAG, "Need to speed down!");
                        //SystemProperties.set("ctl.stop", "inputfreq");
                        //SystemProperties.set("ctl.start", "recoveryfreq");
                        //Slog.i(TAG, "speed down ok!");
                        mThermal.thermalEnabled(true);
                        bSpeeding = false;
                    }
                }

            } while (true);
        }
    }

}
