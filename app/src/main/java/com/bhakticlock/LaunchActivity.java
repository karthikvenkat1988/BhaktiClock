package com.bhakticlock;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * Invisible screen used for "link" and "app" schedules. It turns the screen on,
 * gets past the lock screen (only works when the screen lock is None/Swipe),
 * opens the music app or link, then closes itself.
 */
public class LaunchActivity extends Activity {
    static final String YT_MUSIC = "com.google.android.apps.youtube.music";

    public static Intent targetIntent(Context c, Schedule s) {
        Intent i = null;
        if (Schedule.MODE_LINK.equals(s.mode)) {
            i = new Intent(Intent.ACTION_VIEW, Uri.parse(s.link.trim()));
            String host = i.getData() == null ? null : i.getData().getHost();
            if (host != null && host.contains("music.youtube.com")
                    && c.getPackageManager().getLaunchIntentForPackage(YT_MUSIC) != null) {
                i.setPackage(YT_MUSIC); // open in the app, not the browser
            }
        } else if (Schedule.MODE_APP.equals(s.mode)) {
            i = c.getPackageManager().getLaunchIntentForPackage(s.appPackage);
        }
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km != null && km.isKeyguardLocked()) {
            km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissSucceeded() { openTarget(); }
                @Override public void onDismissError() { openTarget(); }
                @Override public void onDismissCancelled() { openTarget(); }
            });
        } else {
            openTarget();
        }
    }

    private boolean opened = false;

    private void openTarget() {
        if (opened) return;
        opened = true;
        Schedule s = Store.find(this, getIntent().getStringExtra("id"));
        if (s != null) {
            Intent target = targetIntent(this, s);
            try {
                if (target != null) startActivity(target);
                else toast("Couldn't find the music app. Edit the schedule and choose it again.");
            } catch (ActivityNotFoundException e) {
                toast("No app can open this link.");
            }
        }
        finish();
    }

    private void toast(String m) {
        Toast.makeText(getApplicationContext(), m, Toast.LENGTH_LONG).show();
    }
}
