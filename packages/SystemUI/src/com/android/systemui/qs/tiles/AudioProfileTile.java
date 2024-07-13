package com.android.systemui.qs.tiles;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.R;

import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.QSTile.BooleanState;
import com.android.systemui.qs.QSTile.ResourceIcon;

public class AudioProfileTile extends QSTile<QSTile.BooleanState> {

    private AudioManager mAudioManager;

    public AudioProfileTile(Host host) {
        super(host);
        mAudioManager = (AudioManager) mContext
                .getSystemService(Context.AUDIO_SERVICE);
        registerVolumeModeReceiver();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsLogger.QS_AUDIOPROFILE;
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        mContext.unregisterReceiver(mVolumeModeReceiver);
    }

    @Override
    public void setListening(boolean listening) {
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        setAudioProfilModem();
        boolean newState = !mState.value;
        refreshState(newState);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = true;
        state.label = mContext.getString(R.string.quick_settings_volume_label);
        int ringerMode = mAudioManager.getRingerModeInternal();
        if (AudioManager.RINGER_MODE_VIBRATE == ringerMode) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_vibrate_on_sprd);
        } else if (AudioManager.RINGER_MODE_SILENT == ringerMode) {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_silent_on_sprd);
        } else {
            state.icon = ResourceIcon.get(R.drawable.ic_qs_general_on_sprd);
        }
        state.label = mContext.getString(R.string.quick_settings_volume_label);
    }

    private void setAudioProfilModem() {
        int ringerMode = mAudioManager.getRingerModeInternal();
        ContentResolver mResolver = mContext.getContentResolver();
        Vibrator vibrator = (Vibrator) mContext
                .getSystemService(Context.VIBRATOR_SERVICE);
        boolean hasVibrator = vibrator == null ? false : vibrator.hasVibrator();
        if (AudioManager.RINGER_MODE_SILENT == ringerMode) {
            if (hasVibrator) {
                Settings.System.putInt(mResolver,
                        Settings.System.SOUND_EFFECTS_ENABLED, 0);
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_VIBRATE);

                mAudioManager.setVibrateSetting(
                        AudioManager.VIBRATE_TYPE_RINGER,
                        AudioManager.VIBRATE_SETTING_ON);
                mAudioManager.setVibrateSetting(
                        AudioManager.VIBRATE_TYPE_NOTIFICATION,
                        AudioManager.VIBRATE_SETTING_ON);
            } else {
                Settings.System.putInt(mResolver,
                        Settings.System.SOUND_EFFECTS_ENABLED, 1);
                mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
            }
        } else if (AudioManager.RINGER_MODE_VIBRATE == ringerMode) {
            Settings.System.putInt(mResolver,
                    Settings.System.SOUND_EFFECTS_ENABLED, 1);
            mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_NORMAL);
        } else {
            Settings.System.putInt(mResolver,
                    Settings.System.SOUND_EFFECTS_ENABLED, 0);
            mAudioManager.setRingerModeInternal(AudioManager.RINGER_MODE_SILENT);

            if (hasVibrator) {
                mAudioManager.setVibrateSetting(
                        AudioManager.VIBRATE_TYPE_RINGER,
                        AudioManager.VIBRATE_SETTING_OFF);
                mAudioManager.setVibrateSetting(
                        AudioManager.VIBRATE_TYPE_NOTIFICATION,
                        AudioManager.VIBRATE_SETTING_OFF);
            }
        }
    }

    private void registerVolumeModeReceiver() {
        IntentFilter volumeFilter = new IntentFilter();
        volumeFilter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        volumeFilter.addAction(AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION);
        mContext.registerReceiver(mVolumeModeReceiver, volumeFilter);
    }

    private BroadcastReceiver mVolumeModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(AudioManager.RINGER_MODE_CHANGED_ACTION) || action.equals(AudioManager.INTERNAL_RINGER_MODE_CHANGED_ACTION)) {
                int ringerMode = mAudioManager.getRingerModeInternal();
                Log.d(TAG, "mVolumeModeReceiver: action =" + action
                        + " now ringMode=" + ringerMode);
                boolean newState = !mState.value;
                refreshState(newState);
            }
        }
    };
}
