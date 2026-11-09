package com.bhakticlock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.util.Calendar;

/**
 * Arms Android's alarm clock for each schedule. setAlarmClock() is the most reliable
 * timer Android offers: it fires on the exact minute and wakes the phone from deep sleep.
 */
public final class Scheduler {
    public static final String ACTION_FIRE = "com.bhakticlock.FIRE";

    private Scheduler() {}

    /** Next time (millis) this schedule should play after {@code now}, or -1 if never. */
    public static long nextTrigger(Schedule s, long now) {
        if (!s.anyDay()) return -1;
        for (int i = 0; i <= 7; i++) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(now);
            c.add(Calendar.DAY_OF_YEAR, i);
            c.set(Calendar.HOUR_OF_DAY, s.hour);
            c.set(Calendar.MINUTE, s.minute);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            if (c.getTimeInMillis() > now && s.days[c.get(Calendar.DAY_OF_WEEK) - 1]) {
                return c.getTimeInMillis();
            }
        }
        return -1;
    }

    public static long nextTrigger(Schedule s) {
        return nextTrigger(s, System.currentTimeMillis() + 1000);
    }

    private static PendingIntent fireIntent(Context c, Schedule s) {
        Intent i = new Intent(c, AlarmReceiver.class)
                .setAction(ACTION_FIRE)
                .setData(Uri.parse("bhakticlock://schedule/" + s.id))
                .putExtra("id", s.id);
        return PendingIntent.getBroadcast(c, s.requestCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static boolean canUseExact(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        return c.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    public static void schedule(Context c, Schedule s) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = fireIntent(c, s);
        am.cancel(pi);
        if (!s.enabled || !s.hasSource()) return;
        long t = nextTrigger(s);
        if (t < 0) return;
        if (canUseExact(c)) {
            PendingIntent show = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(t, show), pi);
        } else {
            // Fallback (may be a few minutes late). The app shows a warning when this happens.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
        }
    }

    public static void cancel(Context c, Schedule s) {
        c.getSystemService(AlarmManager.class).cancel(fireIntent(c, s));
    }

    public static void rescheduleAll(Context c) {
        for (Schedule s : Store.all(c)) schedule(c, s);
    }
}
