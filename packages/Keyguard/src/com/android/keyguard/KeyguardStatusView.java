/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.keyguard;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.UserHandle;
import android.provider.BaseColumns;
import android.provider.CallLog;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Slog;
import android.util.TypedValue;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import com.android.internal.widget.LockPatternUtils;

import java.util.Locale;
import android.os.Handler;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import java.util.Locale;

public class KeyguardStatusView extends GridLayout {
    private static final boolean DEBUG = KeyguardConstants.DEBUG;
    private static final String TAG = "KeyguardStatusView";

    private final LockPatternUtils mLockPatternUtils;
    private final AlarmManager mAlarmManager;

    private TextView mAlarmStatusView;
    private TextClock mDateView;
    private TextClock mClockView;
    private TextView mOwnerInfo;

    /*bug: 623120 add missCall and missMms notification on lockscreen @{*/
    private LinearLayout mNotificationArea = null;
    private ImageView callImageView = null;
    private ImageView mmsImageView = null;
    private TextView callTextView = null;
    private TextView mmsTextView = null;
    private int miss_calls = 0;
    private int unread_msg = 0;
    private final Handler mHandler = new Handler();
    private static final int QUERY_MISSCALL_MISSMMS_DELAY_MILLIS = 1500;
    private static final String MMS_ACTION = "android.provider.Telephony.SMS_RECEIVED";
    private static final String CALL_ACTION = "android.intent.action.PHONE_STATE";
    private static final String CALL_OUT_ACTION ="android.intent.action.NEW_OUTGOING_CALL";
    /* @} */
    /*bug: 622394 add inCalling notification on lockscreen @{*/
    private TelecomManager telecomManager = null;
    private String mIncomingNumber = null;
    /* @} */
    private KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onTimeChanged() {
            refresh();
        }

        @Override
        public void onKeyguardVisibilityChanged(boolean showing) {
            if (showing) {
                if (DEBUG) Slog.v(TAG, "refresh statusview showing:" + showing);
                refresh();
                updateOwnerInfo();
            }
        }

        @Override
        public void onStartedWakingUp() {
            if (DEBUG) Log.d(TAG, "onStartedWakingUp getCallState: "+telecomManager.getCallState());
            /*bug: 622394 add inCalling notification on lockscreen @{*/
            if (telecomManager.isInCall()) {
                Log.d(TAG, "KeyguardUpdateMonitorCallback isInCall");
                notificationInCall();
            }else if (telecomManager.endCall()) {
                Log.d(TAG, "KeyguardUpdateMonitorCallback endCall");
            }else{
                Log.d(TAG, "KeyguardUpdateMonitorCallback not isInCall");
                /*bug: 623120 add missCall and missMms notification on lockscreen @{*/
                notificationNotInCall();
                updateMissCallAndMissMms();
                /* @} */
            }
            /* @} */
            setEnableMarquee(true);
        }

        @Override
        public void onFinishedGoingToSleep(int why) {
            setEnableMarquee(false);
        }

        @Override
        public void onUserSwitchComplete(int userId) {
            refresh();
            updateOwnerInfo();
        }
    };

    public KeyguardStatusView(Context context) {
        this(context, null, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        mLockPatternUtils = new LockPatternUtils(getContext());
    }

    private void setEnableMarquee(boolean enabled) {
        if (DEBUG) Log.v(TAG, (enabled ? "Enable" : "Disable") + " transport text marquee");
        if (mAlarmStatusView != null) mAlarmStatusView.setSelected(enabled);
        if (mOwnerInfo != null) mOwnerInfo.setSelected(enabled);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAlarmStatusView = (TextView) findViewById(R.id.alarm_status);
        mDateView = (TextClock) findViewById(R.id.date_view);
        mClockView = (TextClock) findViewById(R.id.clock_view);
        mDateView.setShowCurrentUserTime(true);
        mClockView.setShowCurrentUserTime(true);
        mOwnerInfo = (TextView) findViewById(R.id.owner_info);
        /*bug: 623120 add missCall and missMms notification on lockscreen @{*/
        mNotificationArea = (LinearLayout) findViewById(R.id.notification_area);
        callImageView = (ImageView) findViewById(R.id.unread_incall);
        mmsImageView = (ImageView) findViewById(R.id.unread_mms);
        callTextView = (TextView) findViewById(R.id.unread_incall_number);
        mmsTextView = (TextView) findViewById(R.id.unread_mms_number);

        IntentFilter filter = new IntentFilter();
        filter.addAction(MMS_ACTION);
        filter.addAction(CALL_ACTION);
        mContext.registerReceiver(MmsCallReceiver, filter);
        /* @} */
        /* sprd: 626492 @{*/
        if(isTaiMiErLanguage())
        {
            mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.clock_view_middle_font_size));
        }else
        {
            mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.clock_view_big_font_size));
        }
        /* @} */
        /* sprd: 622733 @{*/
        if(isSpecialLanguage())
        {
            mAlarmStatusView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.alarm_view_middle_font_size));
        }else
        {
            mAlarmStatusView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.alarm_view_big_font_size));
        }
        /* @} */
        telecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
        boolean shouldMarquee = KeyguardUpdateMonitor.getInstance(mContext)
                .isDeviceInteractive();
        setEnableMarquee(shouldMarquee);
        refresh();
        updateOwnerInfo();

        // Disable elegant text height because our fancy colon makes the ymin value huge for no
        // reason.
        mClockView.setElegantTextHeight(false);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        /* SPRD: Bug 609454 @{ */
//        mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
//                getResources().getDimensionPixelSize(R.dimen.widget_big_font_size));
//        mDateView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
//                getResources().getDimensionPixelSize(R.dimen.widget_label_font_size));
//        mOwnerInfo.setTextSize(TypedValue.COMPLEX_UNIT_PX,
//                getResources().getDimensionPixelSize(R.dimen.widget_label_font_size));
        /* @{ */
    }

    /* sprd: 626492 @{*/
    private boolean isTaiMiErLanguage()
    {
        String locale = Locale.getDefault().toString();
        Log.d(TAG, " current locale: " + locale);
        if(locale.equalsIgnoreCase("ta_IN") || locale.equalsIgnoreCase("kn_IN"))
        {
            return true;
        }
        return false;
    }
    /* @} */
    /* sprd: 622733 @{*/
    private boolean isSpecialLanguage()
    {
        String locale = Locale.getDefault().toString();
        Log.d(TAG, " current locale: " + locale);
        if(locale.equalsIgnoreCase("ur_PK"))
        {
            return true;
        }
        return false;
    }
    /* @} */
    /*bug: 622394 add inCalling notification on lockscreen @{*/
    private void notificationInCall()
    {
        callTextView.setText(getResources().getString(R.string.in_calling, "calling..."));
        callImageView.setVisibility(View.VISIBLE);
        mmsImageView.setVisibility(View.GONE);
        mmsTextView.setVisibility(View.GONE);
        callTextView.setVisibility(View.VISIBLE);
    }

    private void notificationNotInCall()
    {
        callImageView.setVisibility(View.INVISIBLE);
        mmsImageView.setVisibility(View.INVISIBLE);
        mmsTextView.setVisibility(View.INVISIBLE);
        callTextView.setVisibility(View.INVISIBLE);
    }
    /* @{ */

    /*bug: 623120 add missCall and missMms notification on lockscreen @{*/
    private BroadcastReceiver MmsCallReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            if (DEBUG) Log.d(TAG, "MmsCallReceiver action:"+intent.getAction());

            if(intent.getAction().equals(CALL_ACTION)){
                TelephonyManager tManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                switch (tManager.getCallState()) {
                case TelephonyManager.CALL_STATE_RINGING:
                    mIncomingNumber = intent.getStringExtra("incoming_number");
                    if (DEBUG) Log.i(TAG, "RINGING :" + mIncomingNumber);
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    if (DEBUG) Log.i(TAG, "incoming ACCEPT :" + mIncomingNumber);
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    updateMissCallAndMissMms();
                    if (DEBUG) Log.i(TAG, "incoming IDLE");
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            updateMissCallAndMissMms();
                        }
                    }, QUERY_MISSCALL_MISSMMS_DELAY_MILLIS);
                    break;
                }
            }else{
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        updateMissCallAndMissMms();
                    }
                }, QUERY_MISSCALL_MISSMMS_DELAY_MILLIS);
            }
        }
    };

    private void updateMissCallAndMissMms() {
        miss_calls = getMissCallCount();
        unread_msg = getUnReadMmsCount() + getUnReadSmsCount();
        Log.d(TAG, "updateMissCallAndMissMms miss_calls:" + miss_calls
                + " unread_msg:" + unread_msg);
        if (miss_calls > 0 && unread_msg > 0) {
            callTextView.setText(Integer.toString(miss_calls));
            mmsTextView.setText(Integer.toString(unread_msg));
            callImageView.setVisibility(View.VISIBLE);
            mmsImageView.setVisibility(View.VISIBLE);
            callTextView.setVisibility(View.VISIBLE);
            mmsTextView.setVisibility(View.VISIBLE);
        } else if (miss_calls == 0 && unread_msg == 0) {
            // RightBtn.setVisibility(View.INVISIBLE);
            callImageView.setVisibility(View.GONE);
            mmsImageView.setVisibility(View.GONE);
            callTextView.setVisibility(View.GONE);
            mmsTextView.setVisibility(View.GONE);
            // callTextView.setText(Integer.toString(miss_calls));
            // mmsTextView.setText(Integer.toString(unread_msg));
        } else if (miss_calls == 0 && unread_msg > 0) {
            // RightBtn.setVisibility(View.INVISIBLE);
            mmsTextView.setText(Integer.toString(unread_msg));
            callImageView.setVisibility(View.INVISIBLE);
            mmsImageView.setVisibility(View.VISIBLE);
            callTextView.setVisibility(View.GONE);
            mmsTextView.setVisibility(View.VISIBLE);
            // callTextView.setText(Integer.toString(miss_calls));
        } else if (miss_calls > 0 && unread_msg == 0) {
            // RightBtn.setVisibility(View.INVISIBLE);
            callTextView.setText(Integer.toString(miss_calls));
            callImageView.setVisibility(View.VISIBLE);
            mmsImageView.setVisibility(View.GONE);
            callTextView.setVisibility(View.VISIBLE);
            mmsTextView.setVisibility(View.GONE);
            // mmsTextView.setText(Integer.toString(unread_msg));
        } else {
            Log.d(TAG, "updateMissCallAndMissMms else");
        }
    }

    private int getMissCallCount() {
        Cursor cur = null;
        int unRead = 3;
        try {
            ContentResolver cr = mContext.getContentResolver();
            cur = cr.query(CallLog.Calls.CONTENT_URI, new String[] {
                    CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE, CallLog.Calls.NEW }, "(type = "
                    + unRead + " AND new = 1)", null, "calls.date desc");// limit
            // ?distinct?
            if (cur != null) {
                return cur.getCount();
            }
        } catch (SQLiteException ex) {
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return 0;
    }

    private int getUnReadMmsCount() {
        String selection = "(read=0 AND m_type != 134)";
        Cursor cur = null;
        try {
            ContentResolver cr = mContext.getContentResolver();
            cur = cr.query(Uri.parse("content://mms/inbox"), new String[] {
                    BaseColumns._ID, "date" }, selection, null, "date desc");// limit
            if (cur != null) {
                return cur.getCount();
            }
        } catch (SQLiteException ex) {
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return 0;
    }

    private int getUnReadSmsCount() {
        String selection = "(read=0 OR seen=0)";
        Cursor cur = null;
        try {
            ContentResolver cr = mContext.getContentResolver();
            cur = cr.query(Uri.parse("content://sms/inbox"), new String[] {
                    BaseColumns._ID, "address", "person", "body", "date" },
                    selection, null, "date desc");// limit
            if (cur != null) {
                return cur.getCount();
            }
        } catch (SQLiteException ex) {
        } finally {
            if (cur != null && !cur.isClosed()) {
                cur.close();
            }
        }
        return 0;
    }
    /* @} */
    public void refreshTime() {
        mDateView.setFormat24Hour(Patterns.dateView);
        mDateView.setFormat12Hour(Patterns.dateView);
        /* sprd: 626492 @{*/
        if(isTaiMiErLanguage())
        {
            mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.clock_view_middle_font_size));
        }else
        {
            mClockView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.clock_view_big_font_size));
        }
        /* @{ */
        /* sprd: 622733 @{*/
        if(isSpecialLanguage())
        {
            mAlarmStatusView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.alarm_view_middle_font_size));
        }else
        {
            mAlarmStatusView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.alarm_view_big_font_size));
        }
        /* @{ */
        /* SPRD: Bug 609454 @{ */
        /* SPRD: Bug 474774 set am/pm label @{ */
        // mClockView.setFormat12Hour(Patterns.clockView12);
        mClockView.setFormat12Hour(get12ModeFormat(
                (int) getResources().getDimension(R.dimen.date_view_label_font_size)));
        /* @{ */
        /* @{ */
        mClockView.setFormat24Hour(Patterns.clockView24);
    }

    /* SPRD: Bug 474774 set am/pm label @{ */
    /**
     * @param amPmFontSize - size of am/pm label (label removed is size is 0).
     * @return format string for 12 hours mode time
     */
    public static CharSequence get12ModeFormat(int amPmFontSize) {
        String skeleton = "hma";
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
        // Remove the am/pm
        if (amPmFontSize <= 0) {
            pattern.replaceAll("a", "").trim();
        }
        // Replace spaces with "Hair Space"
        pattern = pattern.replaceAll(" ", "\u200A");
        // Build a spannable so that the am/pm will be formatted
        int amPmPos = pattern.indexOf('a');
        if (amPmPos == -1) {
            return pattern;
        }
        Spannable sp = new SpannableString(pattern);
        sp.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), amPmPos, amPmPos + 1,
                Spannable.SPAN_POINT_MARK);
        sp.setSpan(new AbsoluteSizeSpan(amPmFontSize), amPmPos, amPmPos + 1,
                Spannable.SPAN_POINT_MARK);
        sp.setSpan(new TypefaceSpan("sans-serif-condensed"), amPmPos, amPmPos + 1,
                Spannable.SPAN_POINT_MARK);
        return sp;
    }
    /* @} */

    private void refresh() {
        AlarmManager.AlarmClockInfo nextAlarm =
                mAlarmManager.getNextAlarmClock(UserHandle.USER_CURRENT);
        Patterns.update(mContext, nextAlarm != null);

        refreshTime();
        refreshAlarmStatus(nextAlarm);
    }

    void refreshAlarmStatus(AlarmManager.AlarmClockInfo nextAlarm) {
        if (nextAlarm != null) {
            String alarm = formatNextAlarm(mContext, nextAlarm);
            mAlarmStatusView.setText(alarm);
            mAlarmStatusView.setContentDescription(
                    getResources().getString(R.string.keyguard_accessibility_next_alarm, alarm));
            mAlarmStatusView.setVisibility(View.VISIBLE);
        } else {
            mAlarmStatusView.setVisibility(View.GONE);
        }
    }

    public static String formatNextAlarm(Context context, AlarmManager.AlarmClockInfo info) {
        if (info == null) {
            return "";
        }
        String skeleton = DateFormat.is24HourFormat(context, ActivityManager.getCurrentUser())
                ? "EHm"
                : "Ehma";
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
        return DateFormat.format(pattern, info.getTriggerTime()).toString();
    }

    private void updateOwnerInfo() {
        if (mOwnerInfo == null) return;
        String ownerInfo = getOwnerInfo();
        if (!TextUtils.isEmpty(ownerInfo)) {
            mOwnerInfo.setVisibility(View.VISIBLE);
            mOwnerInfo.setText(ownerInfo);
        } else {
            mOwnerInfo.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mInfoCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mInfoCallback);
    }

    private String getOwnerInfo() {
        ContentResolver res = getContext().getContentResolver();
        String info = null;
        final boolean ownerInfoEnabled = mLockPatternUtils.isOwnerInfoEnabled(
                KeyguardUpdateMonitor.getCurrentUser());
        if (ownerInfoEnabled) {
            info = mLockPatternUtils.getOwnerInfo(KeyguardUpdateMonitor.getCurrentUser());
        }
        return info;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    // DateFormat.getBestDateTimePattern is extremely expensive, and refresh is called often.
    // This is an optimization to ensure we only recompute the patterns when the inputs change.
    private static final class Patterns {
        static String dateView;
        static String clockView12;
        static String clockView24;
        static String cacheKey;

        static void update(Context context, boolean hasAlarm) {
            final Locale locale = Locale.getDefault();
            final Resources res = context.getResources();
            final String dateViewSkel = res.getString(hasAlarm
                    ? R.string.abbrev_wday_month_day_no_year_alarm
                    : R.string.abbrev_wday_month_day_no_year);
            final String clockView12Skel = res.getString(R.string.clock_12hr_format);
            final String clockView24Skel = res.getString(R.string.clock_24hr_format);
            final String key = locale.toString() + dateViewSkel + clockView12Skel + clockView24Skel;
            if (key.equals(cacheKey)) return;

            dateView = DateFormat.getBestDateTimePattern(locale, dateViewSkel);

            clockView12 = DateFormat.getBestDateTimePattern(locale, clockView12Skel);
            // CLDR insists on adding an AM/PM indicator even though it wasn't in the skeleton
            // format.  The following code removes the AM/PM indicator if we didn't want it.
            if (!clockView12Skel.contains("a")) {
                clockView12 = clockView12.replaceAll("a", "").trim();
            }

            clockView24 = DateFormat.getBestDateTimePattern(locale, clockView24Skel);

            // Use fancy colon.
            clockView24 = clockView24.replace(':', '\uee01');
            clockView12 = clockView12.replace(':', '\uee01');

            cacheKey = key;
        }
    }
}
