package com.sprd.systemui.mouse;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.widget.Toast;

import com.android.systemui.R;

public class MouseUI {

    private static final String TAG = "MouseUI";

    private static final String MOUSE_SCROLL_ON = "com.sys.mouse.scroll.on";
    private static final String MOUSE_SCROLL_OFF = "com.sys.mouse.scroll.off";

    private Context mContext = null;

    public void start(Context context) {
        mContext = context;
        Receiver receiver = new Receiver();
        receiver.init();
    }

    private final class Receiver extends BroadcastReceiver {

        public void init() {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(MOUSE_SCROLL_ON);
            filter.addAction(MOUSE_SCROLL_OFF);
            mContext.registerReceiver(this, filter, null, null);
        }

        public void destroy() {
            mContext.unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "MouseUI onReceive action=" + action);
            if (action != null) {
                if (action.equals(MOUSE_SCROLL_ON)) {
                    showToast(mContext.getString(R.string.mouse_scroll_on));
                } else if (action.equals(MOUSE_SCROLL_OFF)) {
                    showToast(mContext.getString(R.string.mouse_scroll_off));
                }
            }
        }
    }

    private void showToast(String text) {
        Toast toast = Toast.makeText(mContext, text, Toast.LENGTH_SHORT);
        toast.show();
    }
}
