package com.android.internal.telephony;

import android.net.Uri;

/**
 * Internal remote interface for Ringer player services.
 *
 * @see com.sprd.phone.RingerService
 *
 * {@hide}
 */
interface IRingerService {
    void play(in Uri ringtone);

    void stop();

    void fadeDownRingtone();

    void maxRingingVolume();
}
