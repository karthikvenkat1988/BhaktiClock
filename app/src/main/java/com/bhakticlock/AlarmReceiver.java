package com.bhakticlock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Called by Android at the scheduled minute. Starts playback and arms the next day. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String id = intent.getStringExtra("id");
        Schedule s = Store.find(c, id);
        if (s == null) return;

        Scheduler.schedule(c, s); // arm the next occurrence first
        if (!s.enabled) return;

        Store.log(c, "Alarm fired for " + s.title() + " (" + s.timeText() + ")");
        if (!Scheduler.canUseExact(c)) {
            Store.log(c, "Warning: \"Alarms & reminders\" is off, so Android may delay or block playback");
        }

        Intent svc = new Intent(c, PlaybackService.class).putExtra("id", s.id);
        try {
            c.startForegroundService(svc);
        } catch (Exception e) {
            Log.e("BhaktiClock", "Could not start playback", e);
            Store.log(c, "Android blocked starting the music: " + e.getClass().getSimpleName());
            // Don't fail silently: offer a one-tap start instead.
            PlaybackService.notifyTapToPlay(c, s);
        }
    }
}
