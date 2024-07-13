package com.android.server.secure;

import java.io.IOException;
import java.io.OutputStream;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;
import android.util.Slog;

public final class TsIpTableCmd {
    private static final String TAG = "TsIpTableCmd";

    private static final boolean LOCAL_DEBUG = true;
    private static TsIpTableCmd instance;

    private static final String SOCKET_PATH = "tsiptableserver";
    private TsIpTableCmd() {
    }

    public static TsIpTableCmd getInstance() {
        if (instance == null) {
            instance = new TsIpTableCmd();
        }
        return instance;
    }

    OutputStream mOut;

    LocalSocket mSocket;

    byte buf[] = new byte[1024];

    int buflen = 0;

    private boolean connect() {
        if (mSocket != null) {
            return true;
        }
        Slog.i(TAG, "socket connecting...");
        try {
            mSocket = new LocalSocket();

            LocalSocketAddress address = new LocalSocketAddress(SOCKET_PATH, LocalSocketAddress.Namespace.RESERVED);
            mSocket.connect(address);
            mOut = mSocket.getOutputStream();
        } catch (IOException ex) {
            Slog.e(TAG, "connect socket error:", ex);
            disconnect();
            return false;
        }
        return true;
    }

    public void disconnect() {
        Slog.i(TAG, "socket disconnecting...");
        try {
            if (mSocket != null)
                mSocket.close();
        } catch (IOException ex) {
            Slog.e(TAG, "disconnect error:", ex);
        }
        try {
            if (mOut != null)
                mOut.close();
        } catch (IOException ex) {
            Slog.e(TAG, "disconnect error:", ex);
        }
        mSocket = null;
        mOut = null;
    }

    private boolean writeCommand(String _cmd) {
        byte[] cmd = _cmd.getBytes();
        int len = cmd.length;
        if ((len < 1) || (len > 1024))
            return false;
        buf[0] = (byte) (len & 0xff);
        buf[1] = (byte) ((len >> 8) & 0xff);
        try {
            mOut.write(buf, 0, 2);
            mOut.write(cmd, 0, len);
        } catch (IOException ex) {
            Slog.e(TAG, "write error", ex);
            disconnect();
            return false;
        }
        return true;
    }

    private synchronized String transaction(String cmd) {
        if (!connect()) {
            Slog.e(TAG, "connection failed");
            return "-1";
        }

        if (!writeCommand(cmd)) {
            /*
             * If installd died and restarted in the background (unlikely but
             * possible) we'll fail on the next write (this one). Try to
             * reconnect and write the command one more time before giving up.
             */
            Slog.e(TAG, "write command failed? reconnect!");
            if (!connect() || !writeCommand(cmd)) {
                return "-1";
            }
        }
        if (LOCAL_DEBUG) {
            Slog.i(TAG, "have sent: '" + cmd + "'");
        }
        return "0";
    }

    private int execute(String cmd) {
        String res = transaction(cmd);
//        Log.d(TAG, "execute cmd:" + cmd);
        try {
            return Integer.parseInt(res);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    public int handleCommand(String command) {
//        Log.d(TAG, "handleCommand=" + command);
        return execute(command);
    }

    public boolean ping() {
        if (execute("ping") < 0) {
            return false;
        } else {
            return true;
        }
    }
}
