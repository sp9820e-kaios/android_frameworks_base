/* Copyright (C) 2016 Spreadtrum Communication Inc. */

package com.android.server.am;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Slog;

import java.util.ArrayList;

public class RejectProcessManager {
    private final ContentResolver mResolver;
    private static final String TAG = "RejectProcessManager";
    private static RejectProcessManager sRejectProcessManager;

    private static int packageNameColIndex;
    private static int allowedColIndex;
    private static int callerPackageColIndex;
    private static int insertTimeColIndex;
    private static int lastUpdateTimeColIndex;

    private String[] mProjectionAll = {RejectProcessRecord.COLUMN_TEXT_PACKAGE_NAME,
            RejectProcessRecord.COLUMN_INTEGER_ALLOWED,
            RejectProcessRecord.COLUMN_TEXT_CALLER_PACKAGE,
            RejectProcessRecord.COLUMN_INTEGER_INSERT_TIME,
            RejectProcessRecord.COLUMN_INTEGER_LAST_UPDATE_TIME};

    public static RejectProcessManager getInstance(Context context) {
        if (sRejectProcessManager == null) {
            sRejectProcessManager = new RejectProcessManager(context);
        }
        return sRejectProcessManager;
    }

    private RejectProcessManager(Context context) {
        mResolver = context.getContentResolver();
    }

    private void setColumnIndex(Cursor rprCursor) {
        if (rprCursor == null) return;

        packageNameColIndex = rprCursor.getColumnIndex(
                RejectProcessRecord.COLUMN_TEXT_PACKAGE_NAME);
        allowedColIndex = rprCursor.getColumnIndex(
                RejectProcessRecord.COLUMN_INTEGER_ALLOWED);
        callerPackageColIndex = rprCursor.getColumnIndex(
                RejectProcessRecord.COLUMN_TEXT_CALLER_PACKAGE);
        insertTimeColIndex = rprCursor.getColumnIndex(
                RejectProcessRecord.COLUMN_INTEGER_INSERT_TIME);
        lastUpdateTimeColIndex = rprCursor.getColumnIndex(
                RejectProcessRecord.COLUMN_INTEGER_LAST_UPDATE_TIME);
        Slog.i(TAG, "showing indexs packageNameColIndex:" + packageNameColIndex
                    + ", allowedColIndex:" + allowedColIndex
                    + ", callerPackageColIndex:" + callerPackageColIndex
                    + ", insertTimeColIndex:" + insertTimeColIndex
                    + ", lastUpdateTimeColIndex:" + lastUpdateTimeColIndex);
    }

    private RejectProcessRecord createRejectProcessRecord(Cursor rejectRecordCursor){
        if (rejectRecordCursor == null) return null;
        RejectProcessRecord rejectProcRecordTmp = new RejectProcessRecord();
        rejectRecordCursor.moveToNext();
        rejectProcRecordTmp.packageName = rejectRecordCursor.getString(packageNameColIndex);
        rejectProcRecordTmp.allowed = rejectRecordCursor.getInt(allowedColIndex);
        rejectProcRecordTmp.callerPackage = rejectRecordCursor.getString(callerPackageColIndex);
        rejectProcRecordTmp.insertTime = rejectRecordCursor.getLong(insertTimeColIndex);
        rejectProcRecordTmp.lastUpdateTime = rejectRecordCursor.getLong(lastUpdateTimeColIndex);

        Slog.i(TAG, "createRejectProcessRecord to return rpr:" + rejectProcRecordTmp.toString());
        if (rejectRecordCursor != null) {
            rejectRecordCursor.close();
            rejectRecordCursor = null;
        }

        return rejectProcRecordTmp;
    }

    public ArrayList<RejectProcessRecord> initialRejectProcessRecords() {
        Cursor rejectRecordCursor = null;
        ArrayList<RejectProcessRecord> rejectProcessRecords = new ArrayList<RejectProcessRecord>();
        try {
            rejectRecordCursor = mResolver.query(
                    RejectProcessRecord.CONTENT_URI,
                    mProjectionAll,
                    null,
                    null,
                    null);
            setColumnIndex(rejectRecordCursor);

            Slog.i(TAG, "->initialRejectProcessRecord The " +
                    "rejectRecordCursor is " + rejectRecordCursor);

            if (rejectRecordCursor != null) {
                while (rejectRecordCursor.moveToNext()) {
                    RejectProcessRecord rejectProcRecordTmp = new RejectProcessRecord();
                    rejectProcRecordTmp.packageName = rejectRecordCursor.getString(
                            packageNameColIndex);
                    rejectProcRecordTmp.allowed = rejectRecordCursor.getInt(allowedColIndex);
                    rejectProcRecordTmp.callerPackage = rejectRecordCursor.getString(
                            callerPackageColIndex);
                    rejectProcRecordTmp.insertTime = rejectRecordCursor.getLong(insertTimeColIndex);
                    rejectProcRecordTmp.lastUpdateTime = rejectRecordCursor.getLong(
                            lastUpdateTimeColIndex);

                    Slog.i(TAG, "add the reject process record:" + rejectProcRecordTmp.toString());

                    rejectProcessRecords.add(rejectProcRecordTmp);
                }
            }
            return rejectProcessRecords;
        }catch (Exception e) {
            Slog.d(TAG, "occur Exception while query reject process records !");
            e.printStackTrace();
        }finally {
            if (rejectRecordCursor != null) rejectRecordCursor.close();
            return rejectProcessRecords;
        }
    }

    public boolean queryRejectProcessRecord(String packageName){
        Cursor rejectProcRecordCursor = null;
        int allowed = ActiveServices.ALLOW_TO_START;
        rejectProcRecordCursor = mResolver.query(
                RejectProcessRecord.CONTENT_URI,
                mProjectionAll,
                RejectProcessRecord.COLUMN_TEXT_PACKAGE_NAME + "=" + packageName,
                null,
                null);
        if (rejectProcRecordCursor != null) {
            int allowedCol = rejectProcRecordCursor.getColumnIndex(
                    RejectProcessRecord.COLUMN_INTEGER_ALLOWED);
            allowed = rejectProcRecordCursor.getShort(allowedCol);

            rejectProcRecordCursor.close();
            rejectProcRecordCursor = null;
        }
        if (allowed > 0) {
            return true;
        }else {
            return false;
        }
    }

    // Get RejectProcessRecord by Id
    public RejectProcessRecord getRejectProcessRecord(long rprId) {
        Cursor rejectProcRecordCursor = null;
        try {
            rejectProcRecordCursor = mResolver.query(
                    RejectProcessRecord.CONTENT_URI,
                    mProjectionAll,
                    RejectProcessRecord.COLUMN_INTEGER_ID + "=" + rprId,
                    null,
                    null);
        }catch (Exception e) {
            Slog.d(TAG, "occur Exception while insert reject process records !");
            e.printStackTrace();
        }

        if (rejectProcRecordCursor != null) {
            setColumnIndex(rejectProcRecordCursor);

            return createRejectProcessRecord(rejectProcRecordCursor);
        }else {
            Slog.d(TAG, "getRejectProcessRecord failed, return null");
            return null;
        }

    }

    private ContentValues convertRejectProcRrdToCv(RejectProcessRecord rpr) {
        ContentValues rprValues = new ContentValues();
        rprValues.put(RejectProcessRecord.COLUMN_TEXT_PACKAGE_NAME, rpr.packageName);
        rprValues.put(RejectProcessRecord.COLUMN_INTEGER_ALLOWED, rpr.allowed);
        rprValues.put(RejectProcessRecord.COLUMN_TEXT_CALLER_PACKAGE, rpr.callerPackage);
        rprValues.put(RejectProcessRecord.COLUMN_INTEGER_INSERT_TIME, rpr.insertTime);
        rprValues.put(RejectProcessRecord.COLUMN_INTEGER_LAST_UPDATE_TIME, rpr.lastUpdateTime);

        return rprValues;
    }

    public boolean insertRejectProcessRecord(RejectProcessRecord rpr) {
        ContentValues rprValues = convertRejectProcRrdToCv(rpr);
        Uri rejectProcessUri = null;
        try {
            rejectProcessUri = mResolver.insert(RejectProcessRecord.CONTENT_URI, rprValues);
        }catch (Exception e) {
            e.printStackTrace();
            Slog.d(TAG, "occur Exception while insert reject process records !");
        }

        //TODO: Print the packagename of the insert Row
        if (rejectProcessUri != null) {
            Slog.d(TAG, "->insertRejectProcessRecord rejectProcessUri:" +
                    rejectProcessUri.toString());
            return true;
        }else {
            return false;
        }
    }

    public boolean updateRejectProcRecord(RejectProcessRecord rpr) {
        ContentValues rprValues = convertRejectProcRrdToCv(rpr);
        Slog.i(TAG, "-> updateRejectProceeRecord to print cv:" + rprValues.toString());
        int impactRowNum = 0;
        try {
            impactRowNum = mResolver.update(
                    RejectProcessRecord.CONTENT_URI,
                    rprValues,
                    RejectProcessRecord.COLUMN_TEXT_PACKAGE_NAME + "=" + "'" +
                        rpr.packageName + "'",
                    null);
        }catch(Exception e) {
            // The Exception stack trace maybe thrown by the ContentResolver#update.
            // To Check the SQLiteException.
            Slog.d(TAG, "occur Exception while update reject process records !");
            e.printStackTrace();
        }

        Slog.i(TAG, "->updateRejectProceeRecord impact row number is " + impactRowNum);
        if (impactRowNum > 0) {
            return true;
        }else {
            return false;
        }
    }

    public boolean deleteRejectProcessRecord(RejectProcessRecord rpr){
        int deletedRowNum = 0;
        try {
            deletedRowNum = mResolver.delete(
                    RejectProcessRecord.CONTENT_URI,
                    RejectProcessRecord.COLUMN_TEXT_PACKAGE_NAME + "=" + rpr.packageName,
                    null);
        }catch (Exception e) {
            Slog.d(TAG, "occur Exception while update reject process records !");
            e.printStackTrace();
        }

        Slog.i(TAG, "->updateRejectProceeRecord deleted row number is " + deletedRowNum);
        if (deletedRowNum > 0) {
            return true;
        }else {
            return false;
        }
    }
}