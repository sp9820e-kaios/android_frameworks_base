/* Copyright (C) 2016 Spreadtrum Communication Inc. */

package com.android.server.am;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.util.Slog;

public class RejectProcessRecordObserver extends ContentObserver {
    private Context mContext;
    private static final String TAG = "RejectProcessRecordObserver";
    private ActivityManagerService mAm;

    public RejectProcessRecordObserver(Context context, ActivityManagerService am) {
        super(am.mHandler);
        context.getContentResolver()
                .registerContentObserver(
                        RejectProcessRecord.CONTENT_URI,
                        false,
                        this
                );
        mContext = context;
        mAm = am;
    }

    // Implement the onChange(boolean) method to delegate the change notification to
    // the onChange(boolean, Uri) method to ensure correct operation on older versions
    // of the framework that did not have the onChange(boolean, Uri) method.
    @Override
    public void onChange(boolean selfChange) {
        this.onChange(selfChange, null);
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        if (uri != null) {
            Slog.d(TAG, "RejectProcessRecordObserver onchange, uri:" + uri.toString());
            mAm.mRejectProcessRecords.clear();
            mAm.mRejectProcessRecords.addAll(mAm.mRejectProcessManager.initialRejectProcessRecords());
            mAm.dumpRejectProcRecords();
        }
    }

    public void unRegister() {
        mContext.getContentResolver().unregisterContentObserver(this);
    }
}