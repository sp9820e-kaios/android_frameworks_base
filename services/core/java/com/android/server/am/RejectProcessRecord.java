/* Copyright (C) 2016 Spreadtrum Communication Inc. */

package com.android.server.am;

import android.net.Uri;

public class RejectProcessRecord {
    // The column name of Reject process record is defined by settings provider
    // AMS just need to follow it.
    public static final Uri CONTENT_URI = Uri.parse("content://settings/apps_start");
    public static final String COLUMN_INTEGER_ID = "_id";
    public static final String COLUMN_TEXT_PACKAGE_NAME = "packageName";
    public static final String COLUMN_INTEGER_ALLOWED = "allowed";// 0:false, 1:true
    public static final String COLUMN_TEXT_CALLER_PACKAGE = "callerPackages";
    public static final String COLUMN_INTEGER_INSERT_TIME = "insertTime";
    public static final String COLUMN_INTEGER_LAST_UPDATE_TIME = "lastupdatetime";

    // flags for check which column is need to update
    public static final int UPDATE_PACKAGE_NAME     = 0x0001;
    public static final int UPDATE_ALLOWED          = 0x0002;
    public static final int UPDATE_CALLER_PACKAGE   = 0x0003;
    public static final int UPDATE_INSERT_TIME      = 0x0004;
    public static final int UPDATE_LAST_UPDATE_TIME = 0x0005;

    public String packageName;
    public int allowed;
    public String callerPackage;
    public long insertTime;
    public long lastUpdateTime;

    public RejectProcessRecord() {
        this(null, 1, null, 0L, 0L);
    }

    public RejectProcessRecord(String packageName, int allowed, String callerPackage,
                                    long insertTime, long lastUpdateTime) {
        this.packageName = packageName;
        this.allowed = allowed;
        this.callerPackage = callerPackage;
        this.insertTime = insertTime;
        this.lastUpdateTime = lastUpdateTime;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RejectProcessRecord
                && this.packageName != null
                && this.callerPackage != null) {
            RejectProcessRecord rpr = (RejectProcessRecord) obj;
            return this.packageName.equals(rpr.packageName)
                    && this.allowed == rpr.allowed
                    && this.callerPackage.equals(rpr.callerPackage)
                    && this.insertTime == rpr.insertTime
                    && this.lastUpdateTime == rpr.lastUpdateTime;
        }
        return super.equals(obj);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("RejectProcessRecord{");
        sb.append(Integer.toHexString(System.identityHashCode(this)));
        sb.append(", packageName:" + this.packageName);
        sb.append(", allowed:" + this.allowed);
        sb.append(", callerPackage:" + this.callerPackage);
        sb.append(", insertTime:" + this.insertTime);
        sb.append(", lastUpdateTime:" + this.lastUpdateTime);
        return sb.toString();
    }

    // check which column has been modified, whether to schedule update the database.
    public int updateFrom(RejectProcessRecord rpr) {
        int changed = 0;
        if (rpr != null && !packageName.equals(rpr.packageName)) {
            packageName = rpr.packageName;
            changed |= UPDATE_PACKAGE_NAME;
        }

        if (rpr != null && allowed != rpr.allowed) {
            allowed = rpr.allowed;
            changed |= UPDATE_ALLOWED;
        }

        if(rpr != null && !callerPackage.equals(rpr.callerPackage)) {
            callerPackage = rpr.callerPackage;
            changed |= UPDATE_CALLER_PACKAGE;
        }

        if ( rpr != null && insertTime != rpr.insertTime) {
            insertTime = rpr.insertTime;
            changed |= UPDATE_INSERT_TIME;
        }

        if (rpr != null && lastUpdateTime != rpr.lastUpdateTime) {
            lastUpdateTime = rpr.lastUpdateTime;
            changed |= UPDATE_LAST_UPDATE_TIME;
        }
        return changed;
    }
}