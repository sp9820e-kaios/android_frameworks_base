package com.thundersoft.secure;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.content.RestrictionEntry;
import android.graphics.Bitmap;
import android.content.Intent;
import android.app.Notification;
/**
 * {@hide}
 */
interface ITsIpTableManager {
    void ServiceReady();
    int getConnectState(String packageName);
    void setConnectState(String packageName, int vaule);
    int getDefaultConnectState();
    void setDefaultConnectSetate(int vaule);
}
