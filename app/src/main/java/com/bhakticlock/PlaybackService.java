package com.bhakticlock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.provider.Settings;
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
    /** Read by the main screen to show a live "Now playing" state. */
    public static volatile String playingId;
    public static volatile String statusText = "";

    private PowerManager.WakeLock wakeLock;
    /** True while a music app / link is playing and we are watching it, so Stop can pause it. */
    private boolean externalActive;
    private int idleChecks;

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

    private static boolean isBtOutput(AudioDeviceInfo d) {
        int t = d.getType();
        return t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                || t == AudioDeviceInfo.TYPE_BLE_SPEAKER
                || t == AudioDeviceInfo.TYPE_BLE_HEADSET;
    }

    /** A connected Bluetooth speaker: one of the chosen ones if any were chosen, otherwise any. */
    public static AudioDeviceInfo findSpeaker(Context c) {
        AudioManager am = c.getSystemService(AudioManager.class);
        List<String[]> allowed = Store.speakers(c);
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (!isBtOutput(d)) continue;
            if (allowed.isEmpty()) return d;
            for (String[] sp : allowed) {
                String name = sp[0], addr = sp[1];
                if (Build.VERSION.SDK_INT >= 28 && !addr.isEmpty() && addr.equalsIgnoreCase(d.getAddress())) return d;
                if (!name.isEmpty() && name.equals(String.valueOf(d.getProductName()))) return d;
            }
        }
        return null;
    }

    public static boolean isBluetoothAudioConnected(Context c) {
        return findSpeaker(c) != null;
    }

    public static boolean notificationAccess(Context c) {
        String enabled = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(c.getPackageName());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureChannel(this);
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            if (externalActive) pauseExternal(); // also pause YouTube / the music app, not just ourselves
            if (playingId != null) Store.log(this, "Stopped by you");
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
        externalActive = false;
        idleChecks = 0;
        acquireWake(); // keep the phone awake while we wait for the speaker
        Store.log(this, "Starting " + s.title());
        current = s;
        playingId = s.id;
        order.clear();
        for (int i = 0; i < s.uris.size(); i++) order.add(i);
        if (s.shuffle) Collections.shuffle(order);
        pos = 0;
        waitedSec = 0;
        updateNotification(s.title(), "Waiting for the speaker…");
        waitForSpeaker();
        return START_NOT_STICKY;
    }

    /** How long to wait for one of the chosen speakers before playing on the phone instead. */
    private static final int SPEAKER_WAIT_SEC = 120;

    private void waitForSpeaker() {
        if (current == null) return;
        boolean specific = Store.hasSpeaker(this);
        int limit = specific ? SPEAKER_WAIT_SEC : MAX_WAIT_SEC;
        AudioDeviceInfo found = findSpeaker(this);
        if (found != null) {
            Store.log(this, "Speaker connected: " + found.getProductName()
                    + (waitedSec > 0 ? " (after " + waitedSec + "s)" : ""));
            startPlayback();
            return;
        }
        if (waitedSec >= limit) {
            Store.log(this, (specific ? Store.speakerLabel(this) : "No speaker")
                    + " not connected after " + limit + "s, playing on the phone");
            if (specific) {
                notifyMessage("Playing on the phone",
                        Store.speakerLabel(this) + " didn't connect within 2 minutes, so the music is playing on the phone's speaker.");
            }
            startPlayback(); // no speaker: Android plays through the phone itself
            return;
        }
        if (waitedSec % 10 == 0) {
            updateNotification(current.title(), "Waiting for " + (specific ? Store.speakerLabel(this) : "the speaker")
                    + " (" + (limit - waitedSec) + "s)");
        }
        waitedSec += 2;
        handler.postDelayed(this::waitForSpeaker, 2000);
    }

    private void notifyMessage(String title, String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        getSystemService(NotificationManager.class).notify(NOTIF_ID + 2,
                new Notification.Builder(this, CHANNEL)
                        .setSmallIcon(android.R.drawable.stat_sys_warning)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setContentIntent(open)
                        .setAutoCancel(true)
                        .build());
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

    private String targetPackage;

    private void launchExternal() {
        final Schedule s = current;
        updateNotification(s.title(), "Opening " + (Schedule.MODE_APP.equals(s.mode) ? s.appName : "link") + "...");
        Store.log(this, "Opening " + (Schedule.MODE_APP.equals(s.mode) ? s.appName : "the link")
                + (notificationAccess(this) ? "" : " (notification access is off, so Play can't be confirmed)")
                + (Settings.canDrawOverlays(this) ? "" : " (display-over-apps is off)"));

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

        // Which app should we press Play in?
        targetPackage = null;
        if (Schedule.MODE_APP.equals(s.mode)) {
            targetPackage = s.appPackage;
        } else {
            Intent t = LaunchActivity.targetIntent(this, s);
            if (t != null) {
                if (t.getPackage() != null) {
                    targetPackage = t.getPackage();
                } else {
                    ResolveInfo ri = getPackageManager().resolveActivity(t, PackageManager.MATCH_DEFAULT_ONLY);
                    if (ri != null && ri.activityInfo != null && !"android".equals(ri.activityInfo.packageName)) {
                        targetPackage = ri.activityInfo.packageName;
                    }
                }
            }
        }

        handler.postDelayed(() -> pollPlay(0), 6000);

        if (s.stopAfterMin > 0) {
            handler.postDelayed(() -> {
                Store.log(this, "Paused after " + s.stopAfterMin + " minutes, as set");
                pauseExternal();
                stopEverything();
            }, s.stopAfterMin * 60_000L);
        }
    }

    /** The music app's media session, if we have notification access and the app has one running. */
    private MediaController findController() {
        if (!notificationAccess(this)) return null;
        try {
            MediaSessionManager msm = getSystemService(MediaSessionManager.class);
            List<MediaController> list = msm.getActiveSessions(new ComponentName(this, MediaListener.class));
            for (MediaController mc : list) {
                if (targetPackage == null || targetPackage.equals(mc.getPackageName())) return mc;
            }
        } catch (SecurityException ignored) {
        }
        return null;
    }

    /** Presses Play in the music app every 3 seconds until it is actually playing (max ~1 minute). */
    private void pollPlay(final int attempt) {
        final Schedule s = current;
        if (s == null) return;
        MediaController mc = findController();
        if (mc != null) {
            PlaybackState st = mc.getPlaybackState();
            if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                Store.log(this, "Music is playing");
                updateNotification(s.title(), "Playing in the music app");
                externalActive = true; // stay alive so the Stop button can pause it
                idleChecks = 0;
                handler.postDelayed(this::watchExternal, 5000);
                return;
            }
            mc.getTransportControls().play();
        } else if (!notificationAccess(this)) {
            // We can't see the music app. Press Play twice, then step aside.
            if (attempt == 0 || attempt == 3) sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
            if (attempt >= 4) {
                Store.log(this, "Pressed Play. Turn on notification access to confirm and to pause from this app");
                if (s.stopAfterMin <= 0) stopEverything();
                return;
            }
        } else if (attempt % 3 == 2) {
            // Access is on, but the app has no media session yet: general Play key press.
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
        }
        if (attempt >= 20) {
            Store.log(this, "The music app opened but did not start playing");
            notifyMessage("Music didn't start",
                    s.title() + " opened but did not start playing. Open it and press Play.");
            if (s.stopAfterMin <= 0) stopEverything();
            return;
        }
        handler.postDelayed(() -> pollPlay(attempt + 1), 3000);
    }

    /** While music plays in another app, keep our Now Playing state true, and finish when it stops. */
    private void watchExternal() {
        if (current == null || !externalActive) return;
        MediaController mc = findController();
        PlaybackState st = mc == null ? null : mc.getPlaybackState();
        boolean active = st != null && (st.getState() == PlaybackState.STATE_PLAYING
                || st.getState() == PlaybackState.STATE_BUFFERING
                || st.getState() == PlaybackState.STATE_CONNECTING);
        idleChecks = active ? 0 : idleChecks + 1;
        if (idleChecks >= 3) { // about 15 seconds paused or gone: you paused it in the app, or it ended
            Store.log(this, "Music stopped in the music app");
            stopEverything();
            return;
        }
        handler.postDelayed(this::watchExternal, 5000);
    }

    private void pauseExternal() {
        MediaController mc = findController();
        if (mc != null) mc.getTransportControls().pause();
        else sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
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
            if (Build.VERSION.SDK_INT >= 28 && Store.hasSpeaker(this)) {
                AudioDeviceInfo dev = findSpeaker(this);
                if (dev != null) mp.setPreferredDevice(dev);
            }
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

    private void acquireWake() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (wakeLock == null) wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bhakticlock:play");
        if (!wakeLock.isHeld()) wakeLock.acquire(6 * 60 * 1000L);
    }

    private void releaseWake() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    /** Shown when Android refuses to start playback by itself: one tap starts it. */
    static void notifyTapToPlay(Context c, Schedule s) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(LAUNCH_CHANNEL) == null) {
            nm.createNotificationChannel(new NotificationChannel(
                    LAUNCH_CHANNEL, "Starting music", NotificationManager.IMPORTANCE_HIGH));
        }
        PendingIntent pi = PendingIntent.getForegroundService(c, s.requestCode(),
                new Intent(c, PlaybackService.class).putExtra("id", s.id),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        nm.notify(NOTIF_ID + 3, new Notification.Builder(c, LAUNCH_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_diya)
                .setContentTitle("Tap to play " + s.title())
                .setContentText("Android stopped it from starting by itself. Allow \"Alarms & reminders\" in the app.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build());
    }

    private void stopEverything() {
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        releaseWake();
        externalActive = false;
        current = null;
        playingId = null;
        getSystemService(NotificationManager.class).cancel(NOTIF_ID + 1);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        playingId = null;
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        releaseWake();
        super.onDestroy();
    }

    private Notification build(String title, String text) {
        int f = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), f);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, PlaybackService.class).setAction(ACTION_STOP), f);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_diya)
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
        statusText = text;
        getSystemService(NotificationManager.class).notify(NOTIF_ID, build(title, text));
    }
}
