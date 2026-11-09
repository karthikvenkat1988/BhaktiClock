package com.bhakticlock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.view.KeyEvent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Plays a schedule's audio files through the media stream, so the sound goes to the
 * connected Bluetooth speaker. Runs as a foreground service so Android won't kill it
 * mid-song (that's why a "Now playing" notification is shown).
 */
public class PlaybackService extends Service {
    public static final String ACTION_STOP = "com.bhakticlock.STOP";
    static final String CHANNEL = "playback";
    static final String LAUNCH_CHANNEL = "launch";
    private static final int NOTIF_ID = 7;
    /** How long to wait for the Bluetooth speaker to (re)connect before playing anyway. */
    private static final int MAX_WAIT_SEC = 30;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Integer> order = new ArrayList<>();
    private MediaPlayer player;
    private Schedule current;
    private int pos;
    private int waitedSec;

    public static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "Now playing", NotificationManager.IMPORTANCE_LOW));
        }
    }

    public static boolean isBluetoothAudioConnected(Context c) {
        AudioManager am = c.getSystemService(AudioManager.class);
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int t = d.getType();
            if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    || t == AudioDeviceInfo.TYPE_BLE_SPEAKER
                    || t == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                return true;
            }
        }
        return false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureChannel(this);
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopEverything();
            return START_NOT_STICKY;
        }

        goForeground("Bhakti Clock", "Getting ready…");

        Schedule s = intent == null ? null : Store.find(this, intent.getStringExtra("id"));
        if (s == null || !s.hasSource()) {
            stopEverything();
            return START_NOT_STICKY;
        }

        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        current = s;
        order.clear();
        for (int i = 0; i < s.uris.size(); i++) order.add(i);
        if (s.shuffle) Collections.shuffle(order);
        pos = 0;
        waitedSec = 0;
        updateNotification(s.title(), "Waiting for the speaker…");
        waitForSpeaker();
        return START_NOT_STICKY;
    }

    private void waitForSpeaker() {
        if (current == null) return;
        if (isBluetoothAudioConnected(this) || waitedSec >= MAX_WAIT_SEC) {
            startPlayback();
            return;
        }
        waitedSec += 2;
        handler.postDelayed(this::waitForSpeaker, 2000);
    }

    private void startPlayback() {
        AudioManager am = getSystemService(AudioManager.class);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int vol = Math.max(1, Math.round(max * current.volume / 100f));
        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
        } catch (SecurityException ignored) {
            // Some phones block volume changes while Do Not Disturb is on.
        }
        if (!Schedule.MODE_FILES.equals(current.mode)) {
            launchExternal();
            return;
        }
        playCurrent();
    }

    // ---- "open a music app / link" mode ------------------------------------------

    private void launchExternal() {
        final Schedule s = current;
        updateNotification(s.title(), "Opening " + (Schedule.MODE_APP.equals(s.mode) ? s.appName : "link") + "\u2026");

        Intent launch = new Intent(this, LaunchActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("id", s.id);

        // 1) Full-screen notification: the supported way to wake the screen and open an app.
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(LAUNCH_CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(
                    LAUNCH_CHANNEL, "Starting music", NotificationManager.IMPORTANCE_HIGH));
        }
        PendingIntent pi = PendingIntent.getActivity(this, 2, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        nm.notify(NOTIF_ID + 1, new Notification.Builder(this, LAUNCH_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Starting " + s.title())
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setAutoCancel(true)
                .setTimeoutAfter(60000)
                .build());

        // 2) Direct start (works when "Display over other apps" is allowed).
        try {
            startActivity(launch);
        } catch (Exception ignored) {
        }

        // Press Play a few times: apps take a while to load, and PLAY (unlike play/pause)
        // does nothing if the music is already running.
        int[] delays = {8000, 15000, 25000};
        for (int d : delays) handler.postDelayed(() -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY), d);

        long end = s.stopAfterMin > 0 ? s.stopAfterMin * 60_000L : 40_000L;
        handler.postDelayed(() -> {
            if (s.stopAfterMin > 0) sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
            stopEverything();
        }, end);
    }

    private void sendMediaKey(int code) {
        AudioManager am = getSystemService(AudioManager.class);
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }

    private void playCurrent() {
        releasePlayer();
        if (current == null || pos >= order.size()) {
            stopEverything();
            return;
        }
        int idx = order.get(pos);
        String name = idx < current.names.size() ? current.names.get(idx) : "Track " + (idx + 1);

        MediaPlayer mp = new MediaPlayer();
        player = mp;
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            mp.setDataSource(this, Uri.parse(current.uris.get(idx)));
            mp.setOnPreparedListener(MediaPlayer::start);
            mp.setOnCompletionListener(p -> handler.post(this::next));
            mp.setOnErrorListener((p, what, extra) -> {
                handler.post(this::next);
                return true;
            });
            mp.prepareAsync();
            updateNotification(current.title(), "Playing: " + name);
        } catch (Exception e) {
            // File missing or unreadable: skip to the next one.
            handler.post(this::next);
        }
    }

    private void next() {
        pos++;
        playCurrent();
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.reset();
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
    }

    private void stopEverything() {
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        current = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        super.onDestroy();
    }

    private Notification build(String title, String text) {
        int f = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), f);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, PlaybackService.class).setAction(ACTION_STOP), f);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource("android", android.R.drawable.ic_media_pause),
                        "Stop", stop).build())
                .build();
    }

    private void goForeground(String title, String text) {
        Notification n = build(title, text);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void updateNotification(String title, String text) {
        getSystemService(NotificationManager.class).notify(NOTIF_ID, build(title, text));
    }
}
