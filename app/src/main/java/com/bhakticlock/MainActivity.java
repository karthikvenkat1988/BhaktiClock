package com.bhakticlock;

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioDeviceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_AUDIO = 42;
    private static final int REQ_NOTIF = 1;
    private static final int REQ_BT = 2;

    // Warm "temple lamp" palette
    static final int BG = 0xFFFFF8F0;
    static final int INK = 0xFF2B1A12;
    static final int INK_SOFT = 0xFF7A6558;
    static final int SAFFRON = 0xFFC2410C;
    static final int MAROON = 0xFF7C2D12;
    static final int GOLD = 0xFFF59E0B;
    static final int LINE = 0xFFF1E4D8;
    static final int CREAM = 0xFFFFEDD5;
    static final int GREEN = 0xFF16A34A;
    static final int RED = 0xFFDC2626;

    private static final String E_SUNRISE = "🌅";
    private static final String E_SUN = "☀️";
    private static final String E_SUNSET = "🌇";
    private static final String E_MOON = "🌙";
    private static final String E_DAWN = "🌄";
    private static final String E_NOTE = "🎵";
    private static final String E_PHONE = "📱";
    private static final String E_LINK = "🔗";
    private static final String E_DIYA = "🪔";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Animator> running = new ArrayList<>();
    private final Random rnd = new Random();

    private LinearLayout content;
    private ExtendedFloatingActionButton fab;
    private TextView clockText, ampmText, dateText, greetText, nextText, speakerText, playingText;
    private View speakerDot;
    private LinearLayout playingPill;
    private String lastPlayingId = "";
    private boolean lastBt;
    private boolean animateEntrance = true;
    private boolean exactPrompted;
    private int insetTop, insetBottom;

    // Schedule being edited in the bottom sheet
    private Schedule draft;
    private TextView draftFiles;

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        PlaybackService.ensureChannel(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        ScrollView sv = new ScrollView(this);
        sv.setClipToPadding(false);
        sv.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(112));
        sv.addView(content);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));

        fab = new ExtendedFloatingActionButton(this);
        fab.setText("Add schedule");
        fab.setAllCaps(false);
        fab.setTextColor(Color.WHITE);
        fab.setIconResource(R.drawable.ic_add);
        fab.setIconTint(ColorStateList.valueOf(Color.WHITE));
        fab.setBackgroundTintList(ColorStateList.valueOf(SAFFRON));
        fab.setOnClickListener(v -> {
            haptic(v);
            showEditor(null, null, -1, -1);
        });
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        flp.setMargins(0, 0, dp(20), dp(24));
        root.addView(fab, flp);
        setContentView(root);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (sb.top != insetTop || sb.bottom != insetBottom) {
                insetTop = sb.top;
                insetBottom = sb.bottom;
                content.setPadding(0, 0, 0, dp(112) + insetBottom);
                FrameLayout.LayoutParams fp = (FrameLayout.LayoutParams) fab.getLayoutParams();
                fp.bottomMargin = dp(24) + insetBottom;
                fp.rightMargin = dp(20) + sb.right;
                fab.setLayoutParams(fp);
                render();
            }
            return insets;
        });

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Scheduler.rescheduleAll(this);
        animateEntrance = true;
        render();
        promptExactAlarm();
        ui.removeCallbacks(tick);
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
        stopAnimations();
    }

    /** Runs every second: live clock, countdown, speaker and "now playing" state. */
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            String pid = String.valueOf(PlaybackService.playingId);
            boolean bt = PlaybackService.isBluetoothAudioConnected(MainActivity.this);
            if (!pid.equals(lastPlayingId) || bt != lastBt) {
                render();
            } else {
                updateLive();
            }
            ui.postDelayed(this, 1000);
        }
    };

    // ------------------------------------------------------------------ main screen

    private void render() {
        stopAnimations();
        content.removeAllViews();
        content.addView(hero());
        content.addView(statusRow());
        View setup = setupCard();
        if (setup != null) content.addView(setup);

        List<Schedule> list = Store.all(this);
        LinearLayout head = row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(24), dp(26), dp(24), dp(2));
        TextView h = text("Your schedules", 21, INK);
        h.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        head.addView(h, new LinearLayout.LayoutParams(0, -2, 1));
        if (!list.isEmpty()) {
            TextView count = text(String.valueOf(list.size()), 13, MAROON);
            count.setTypeface(Typeface.DEFAULT_BOLD);
            count.setGravity(Gravity.CENTER);
            count.setBackground(rounded(CREAM, dp(14)));
            head.addView(count, new LinearLayout.LayoutParams(dp(28), dp(28)));
        }
        content.addView(head);

        if (list.isEmpty()) {
            content.addView(emptyState());
        } else {
            int i = 0;
            for (Schedule s : list) content.addView(card(s, i++));
            TextView tip = text("Tip: tap a card to edit it, long-press to delete.", 12, INK_SOFT);
            tip.setGravity(Gravity.CENTER);
            tip.setPadding(dp(24), dp(18), dp(24), 0);
            content.addView(tip);
        }

        TextView hist = text("View history", 13, SAFFRON);
        hist.setTypeface(Typeface.DEFAULT_BOLD);
        hist.setGravity(Gravity.CENTER);
        hist.setPadding(dp(24), dp(14), dp(24), dp(14));
        hist.setOnClickListener(v -> showHistory());
        content.addView(hist);

        lastPlayingId = String.valueOf(PlaybackService.playingId);
        lastBt = PlaybackService.isBluetoothAudioConnected(this);
        animateEntrance = false;
        updateLive();
    }

    private void updateLive() {
        if (clockText == null) return;
        Calendar c = Calendar.getInstance();
        clockText.setText(DateFormat.format("h:mm", c));
        ampmText.setText(DateFormat.format("a", c));
        dateText.setText(DateFormat.format("EEEE, d MMMM", c));
        greetText.setText(greeting(c.get(Calendar.HOUR_OF_DAY)));
        nextText.setText(nextUpText());

        AudioDeviceInfo sp = PlaybackService.findSpeaker(this);
        String label = Store.speakerLabel(this);
        if (sp != null) {
            speakerText.setText(sp.getProductName() + " connected");
            tintOval(speakerDot, GREEN);
        } else {
            speakerText.setText(label.isEmpty() ? "No speaker connected" : label + " · off");
            tintOval(speakerDot, 0xFFB8A79A);
        }

        String pid = PlaybackService.playingId;
        playingPill.setVisibility(pid != null ? View.VISIBLE : View.GONE);
        if (pid != null) {
            String st = PlaybackService.statusText;
            playingText.setText((TextUtils.isEmpty(st) ? "Playing" : st) + "  ·  Stop");
        }
    }

    private View hero() {
        FrameLayout h = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{MAROON, SAFFRON, GOLD});
        float r = dp(34);
        g.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        h.setBackground(g);
        h.setPadding(dp(24), dp(18) + insetTop, dp(20), dp(26));

        LinearLayout col = vertical();
        col.setPadding(0, 0, dp(84), 0);
        greetText = text("", 17, 0xFFFFE7C2);
        greetText.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
        col.addView(greetText);

        LinearLayout clockRow = row();
        clockRow.setGravity(Gravity.BOTTOM);
        clockText = text("", 62, Color.WHITE);
        clockText.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clockText.setIncludeFontPadding(false);
        clockRow.addView(clockText);
        ampmText = text("", 18, 0xFFFFE7C2);
        ampmText.setPadding(dp(6), 0, 0, dp(10));
        clockRow.addView(ampmText);
        col.addView(clockRow);

        dateText = text("", 14, 0xFFFFD9B0);
        col.addView(dateText);

        nextText = text("", 14, Color.WHITE);
        nextText.setBackground(rounded(0x33FFFFFF, dp(18)));
        nextText.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = dp(16);
        col.addView(nextText, nlp);

        h.addView(col, new FrameLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(96), dp(116), Gravity.END | Gravity.TOP);
        h.addView(diya(), dlp);
        return h;
    }

    /** The lamp in the header: a glowing, flickering flame over the diya. */
    private View diya() {
        FrameLayout f = new FrameLayout(this);

        View glow = new View(this);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        gd.setColors(new int[]{0xB3FFF3C4, 0x00FFF3C4});
        gd.setGradientRadius(dp(48));
        glow.setBackground(gd);
        f.addView(glow, new FrameLayout.LayoutParams(dp(96), dp(96), Gravity.CENTER_HORIZONTAL | Gravity.TOP));

        ImageView flame = new ImageView(this);
        flame.setImageResource(R.drawable.ic_flame);
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(dp(30), dp(50), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        fl.topMargin = dp(20);
        f.addView(flame, fl);

        ImageView bowl = new ImageView(this);
        bowl.setImageResource(R.drawable.ic_diya_bowl);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(dp(70), dp(39), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        bl.topMargin = dp(64);
        f.addView(bowl, bl);

        flame.setPivotX(dp(15));
        flame.setPivotY(dp(47));
        loop(ObjectAnimator.ofFloat(flame, View.SCALE_Y, 1f, 1.13f, 0.95f, 1.07f, 1f), 1700);
        loop(ObjectAnimator.ofFloat(flame, View.SCALE_X, 1f, 0.93f, 1.04f, 0.96f, 1f), 1250);
        loop(ObjectAnimator.ofFloat(flame, View.ROTATION, -2f, 3f, -1.5f, 2f, -2f), 2600);
        loop(ObjectAnimator.ofFloat(glow, View.ALPHA, 0.55f, 1f, 0.7f, 0.95f, 0.55f), 2300);
        loop(ObjectAnimator.ofFloat(glow, View.SCALE_X, 0.92f, 1.06f, 0.92f), 2300);
        loop(ObjectAnimator.ofFloat(glow, View.SCALE_Y, 0.92f, 1.06f, 0.92f), 2300);

        f.setOnClickListener(v -> {
            haptic(v);
            bounce(flame);
            toast("🙏");
        });
        return f;
    }

    private View statusRow() {
        LinearLayout r = vertical();
        r.setPadding(dp(20), dp(16), dp(20), 0);

        LinearLayout sp = pill(Color.WHITE, true);
        speakerDot = new View(this);
        sp.addView(speakerDot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_speaker);
        ic.setImageTintList(ColorStateList.valueOf(INK_SOFT));
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(dp(18), dp(18));
        il.setMargins(dp(8), 0, dp(6), 0);
        sp.addView(ic, il);
        speakerText = text("", 14, INK);
        speakerText.setMaxLines(1);
        speakerText.setEllipsize(TextUtils.TruncateAt.END);
        sp.addView(speakerText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView change = text("Change", 13, SAFFRON);
        change.setTypeface(Typeface.DEFAULT_BOLD);
        change.setPadding(dp(8), 0, 0, 0);
        sp.addView(change);
        sp.setOnClickListener(v -> {
            haptic(v);
            chooseSpeaker();
        });
        r.addView(sp, new LinearLayout.LayoutParams(-1, -2));

        playingPill = pill(SAFFRON, false);
        playingPill.addView(equalizer(Color.WHITE, 16));
        playingText = text("", 14, Color.WHITE);
        playingText.setTypeface(Typeface.DEFAULT_BOLD);
        playingText.setPadding(dp(10), 0, 0, 0);
        playingText.setMaxLines(1);
        playingText.setEllipsize(TextUtils.TruncateAt.END);
        playingPill.addView(playingText, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView stop = new ImageView(this);
        stop.setImageResource(R.drawable.ic_stop);
        stop.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        playingPill.addView(stop, new LinearLayout.LayoutParams(dp(20), dp(20)));
        playingPill.setOnClickListener(v -> {
            haptic(v);
            stopMusic();
        });
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, -2);
        pl.topMargin = dp(10);
        r.addView(playingPill, pl);
        return r;
    }

    private LinearLayout pill(int color, boolean outlined) {
        LinearLayout p = row();
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable g = rounded(color, dp(24));
        if (outlined) g.setStroke(dp(1), LINE);
        p.setBackground(g);
        p.setClickable(true);
        return p;
    }

    /** "Finish setup" checklist; hidden once everything is done. */
    private View setupCard() {
        boolean external = false;
        for (Schedule s : Store.all(this)) if (!Schedule.MODE_FILES.equals(s.mode)) external = true;

        final List<Object[]> items = new ArrayList<>(); // {label, done, fix}
        items.add(new Object[]{"Play on the exact minute", Scheduler.canUseExact(this), (Runnable) () ->
                open(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())))});
        PowerManager pm = getSystemService(PowerManager.class);
        items.add(new Object[]{"Never put the app to sleep", pm.isIgnoringBatteryOptimizations(getPackageName()), (Runnable) () ->
                open(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())))});
        if (Build.VERSION.SDK_INT >= 33) {
            items.add(new Object[]{"Show a Stop button while playing",
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                    (Runnable) () -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF)});
        }
        if (external) {
            items.add(new Object[]{"Open music apps by itself", Settings.canDrawOverlays(this), (Runnable) () ->
                    open(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))});
            items.add(new Object[]{"Press Play in the music app", PlaybackService.notificationAccess(this), (Runnable) () ->
                    open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))});
            if (Build.VERSION.SDK_INT >= 34) {
                items.add(new Object[]{"Wake the screen to open music",
                        getSystemService(NotificationManager.class).canUseFullScreenIntent(), (Runnable) () ->
                        open(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.parse("package:" + getPackageName())))});
            }
        }
        items.add(new Object[]{"Choose your speakers", Store.hasSpeaker(this), (Runnable) this::chooseSpeaker});

        int done = 0;
        for (Object[] it : items) if ((Boolean) it[1]) done++;
        if (done == items.size()) return null;

        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(24));
        card.setCardElevation(0);
        card.setCardBackgroundColor(0xFFFFF4E0);
        card.setStrokeColor(0xFFFCD9A8);
        card.setStrokeWidth(dp(1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(20), dp(14), dp(20), 0);
        card.setLayoutParams(lp);

        LinearLayout in = vertical();
        in.setPadding(dp(18), dp(16), dp(18), dp(10));
        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = text("Finish setup", 17, INK);
        t.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        top.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(text(done + " of " + items.size() + " done", 13, INK_SOFT));
        in.addView(top);

        LinearProgressIndicator bar = new LinearProgressIndicator(this);
        bar.setMax(items.size());
        bar.setProgressCompat(done, false);
        bar.setIndicatorColor(SAFFRON);
        bar.setTrackColor(0xFFFCE3C4);
        bar.setTrackThickness(dp(6));
        bar.setTrackCornerRadius(dp(3));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2);
        bp.setMargins(0, dp(10), 0, dp(6));
        in.addView(bar, bp);

        for (Object[] it : items) {
            final boolean ok = (Boolean) it[1];
            final Runnable fix = (Runnable) it[2];
            LinearLayout r = row();
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, dp(9), 0, dp(9));
            ImageView ic = new ImageView(this);
            ic.setImageResource(ok ? R.drawable.ic_check : R.drawable.ic_warning);
            ic.setImageTintList(ColorStateList.valueOf(ok ? GREEN : 0xFFD97706));
            r.addView(ic, new LinearLayout.LayoutParams(dp(20), dp(20)));
            TextView l = text((String) it[0], 15, ok ? INK_SOFT : INK);
            l.setPadding(dp(12), 0, 0, 0);
            r.addView(l, new LinearLayout.LayoutParams(0, -2, 1));
            if (!ok) {
                TextView go = text("Fix ›", 14, SAFFRON);
                go.setTypeface(Typeface.DEFAULT_BOLD);
                r.addView(go);
                r.setOnClickListener(v -> {
                    haptic(v);
                    fix.run();
                });
            }
            in.addView(r);
        }
        card.addView(in);
        return card;
    }

    private View emptyState() {
        LinearLayout e = vertical();
        e.setGravity(Gravity.CENTER_HORIZONTAL);
        e.setPadding(dp(28), dp(24), dp(28), 0);
        TextView lamp = text(E_DIYA, 56, INK);
        e.addView(lamp);
        loop(ObjectAnimator.ofFloat(lamp, View.TRANSLATION_Y, 0f, -dp(6), 0f), 2400);
        TextView t = text("Nothing scheduled yet", 20, INK);
        t.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        t.setPadding(0, dp(8), 0, dp(6));
        e.addView(t);
        TextView b = text("Start with one of these, or tap “Add schedule”.", 14, INK_SOFT);
        b.setGravity(Gravity.CENTER);
        e.addView(b);

        ChipGroup g = new ChipGroup(this);
        g.setPadding(0, dp(14), 0, 0);
        addSuggestion(g, E_SUNRISE + "  Suprabhatam at 6:00 AM", "Morning Suprabhatam", 6, 0);
        addSuggestion(g, E_SUNSET + "  Vishnu Sahasranamam at 6:00 PM", "Evening Vishnu Sahasranamam", 18, 0);
        addSuggestion(g, E_MOON + "  Bhajans at 8:30 PM", "Night bhajans", 20, 30);
        e.addView(g);
        return e;
    }

    private void addSuggestion(ChipGroup g, String chipText, final String label, final int h, final int m) {
        Chip c = new Chip(this);
        c.setText(chipText);
        c.setChipBackgroundColor(ColorStateList.valueOf(CREAM));
        c.setTextColor(MAROON);
        c.setChipStrokeWidth(0);
        c.setOnClickListener(v -> {
            haptic(v);
            showEditor(null, label, h, m);
        });
        g.addView(c);
    }

    private View card(final Schedule s, int index) {
        final int[] acc = accent(s.hour);
        final boolean playing = s.id.equals(PlaybackService.playingId);

        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(24));
        card.setCardElevation(playing ? dp(6) : 0);
        card.setCardBackgroundColor(Color.WHITE);
        card.setStrokeWidth(dp(playing ? 2 : 1));
        card.setStrokeColor(playing ? acc[0] : LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(20), dp(12), dp(20), 0);
        card.setLayoutParams(lp);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> showEditor(s, null, -1, -1));
        card.setOnLongClickListener(v -> {
            haptic(v);
            confirmDelete(s, null);
            return true;
        });

        LinearLayout in = vertical();
        in.setPadding(dp(16), dp(16), dp(14), dp(14));

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = text(emoji(s.hour), 24, Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, acc);
        bg.setShape(GradientDrawable.OVAL);
        badge.setBackground(bg);
        top.addView(badge, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout mid = vertical();
        mid.setPadding(dp(14), 0, dp(8), 0);
        LinearLayout timeRow = row();
        timeRow.setGravity(Gravity.BOTTOM);
        String[] tp = s.timeText().split(" ");
        TextView t = text(tp[0], 30, INK);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        timeRow.addView(t);
        TextView ap = text(tp.length > 1 ? tp[1] : "", 13, acc[0]);
        ap.setTypeface(Typeface.DEFAULT_BOLD);
        ap.setPadding(dp(5), 0, 0, dp(3));
        timeRow.addView(ap);
        mid.addView(timeRow);
        TextView title = text(s.title(), 16, INK);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(title);
        TextView src = text(sourceLine(s), 13, INK_SOFT);
        src.setMaxLines(1);
        src.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(src);
        top.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));

        MaterialSwitch sw = new MaterialSwitch(this);
        sw.setChecked(s.enabled);
        sw.setOnCheckedChangeListener((v, on) -> {
            haptic(v);
            s.enabled = on;
            Store.upsert(this, s);
            Scheduler.schedule(this, s);
            ui.post(this::render);
        });
        top.addView(sw);
        in.addView(top);

        LinearLayout bottom = row();
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(0, dp(14), 0, 0);
        int[] order = {1, 2, 3, 4, 5, 6, 0};
        String[] letters = {"M", "T", "W", "T", "F", "S", "S"};
        for (int k = 0; k < 7; k++) {
            boolean on = s.days[order[k]];
            TextView d = text(letters[k], 11, on ? Color.WHITE : INK_SOFT);
            d.setGravity(Gravity.CENTER);
            d.setTypeface(Typeface.DEFAULT_BOLD);
            GradientDrawable dg = new GradientDrawable();
            dg.setShape(GradientDrawable.OVAL);
            dg.setColor(on ? acc[0] : 0xFFF5EDE6);
            d.setBackground(dg);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(25), dp(25));
            dl.setMargins(0, 0, dp(5), 0);
            bottom.addView(d, dl);
        }
        bottom.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        MaterialButton play = roundIcon(playing ? R.drawable.ic_stop : R.drawable.ic_play, acc[0]);
        play.setOnClickListener(v -> {
            haptic(v);
            bounce(v);
            if (playing) {
                stopMusic();
            } else {
                startForegroundService(new Intent(this, PlaybackService.class).putExtra("id", s.id));
                Snackbar.make(content, "Starting " + s.title() + "…", Snackbar.LENGTH_SHORT)
                        .setAnchorView(fab).show();
            }
        });
        bottom.addView(play, new LinearLayout.LayoutParams(dp(46), dp(46)));
        in.addView(bottom);

        LinearLayout foot = row();
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding(0, dp(10), 0, 0);
        if (playing) {
            foot.addView(equalizer(acc[0], 14));
            TextView pt = text("Now playing", 13, acc[0]);
            pt.setTypeface(Typeface.DEFAULT_BOLD);
            pt.setPadding(dp(8), 0, 0, 0);
            foot.addView(pt);
        } else {
            long next = Scheduler.nextTrigger(s);
            String nt = !s.enabled ? "Paused" : next < 0 ? "Not scheduled"
                    : "Next: " + DateFormat.format("EEE d MMM", next) + "  ·  " + countdown(next - System.currentTimeMillis());
            foot.addView(text(nt, 12, INK_SOFT));
        }
        in.addView(foot);

        card.addView(in);
        float target = s.enabled ? 1f : 0.6f;
        if (animateEntrance) {
            card.setAlpha(0f);
            card.setTranslationY(dp(28));
            card.animate().alpha(target).translationY(0).setStartDelay(70L * index)
                    .setDuration(420).setInterpolator(new DecelerateInterpolator(2f)).start();
        } else {
            card.setAlpha(target);
        }
        return card;
    }

    private void confirmDelete(final Schedule s, final BottomSheetDialog sheet) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete this schedule?")
                .setMessage(s.title() + " at " + s.timeText())
                .setPositiveButton("Delete", (d, w) -> {
                    Scheduler.cancel(this, s);
                    Store.remove(this, s.id);
                    if (sheet != null) sheet.dismiss();
                    render();
                    Snackbar.make(content, "Deleted", Snackbar.LENGTH_SHORT).setAnchorView(fab).show();
                })
                .setNegativeButton("Keep", null)
                .show();
    }

    /** Without this permission Android can delay the alarm or block playback, so ask clearly. */
    private void promptExactAlarm() {
        if (exactPrompted || Scheduler.canUseExact(this) || Store.all(this).isEmpty()) return;
        exactPrompted = true;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Allow \"Alarms & reminders\"")
                .setMessage("Without this, Android can start your music late, or not at all.\n\n"
                        + "Tap Open settings and switch it on for this app.")
                .setPositiveButton("Open settings", (d, w) -> open(new Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))))
                .setNegativeButton("Later", null)
                .show();
    }

    private void showHistory() {
        List<Object[]> logs = Store.logs(this);
        if (logs.isEmpty()) {
            toast("Nothing yet. Events show here when a schedule runs.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Object[] e : logs) {
            sb.append(DateFormat.format("EEE d MMM, h:mm:ss a", (Long) e[0])).append("\n")
                    .append((String) e[1]).append("\n\n");
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("What happened")
                .setMessage(sb.toString().trim())
                .setPositiveButton("Close", null)
                .setNeutralButton("Clear", (d, w) -> Store.clearLog(this))
                .show();
    }

    private void stopMusic() {
        startService(new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_STOP));
        ui.postDelayed(this::render, 300);
    }

    // ------------------------------------------------------------------ editor (bottom sheet)

    private void showEditor(final Schedule existing, String presetLabel, int ph, int pm) {
        draft = existing == null ? new Schedule() : existing.copy();
        if (existing == null && ph >= 0) {
            draft.hour = ph;
            draft.minute = pm;
            draft.label = presetLabel;
        }
        final BottomSheetDialog sheet = new BottomSheetDialog(this);

        LinearLayout v = vertical();
        v.setPadding(dp(24), dp(10), dp(24), dp(28));

        View grab = new View(this);
        grab.setBackground(rounded(0xFFE5D5C8, dp(3)));
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(dp(40), dp(5));
        gl.gravity = Gravity.CENTER_HORIZONTAL;
        gl.bottomMargin = dp(14);
        v.addView(grab, gl);

        TextView h = text(existing == null ? "New schedule" : "Edit schedule", 23, INK);
        h.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        v.addView(h);

        // Big tappable time -> clock-face picker
        final TextView timeView = text(draft.timeText(), 46, SAFFRON);
        timeView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        timeView.setGravity(Gravity.CENTER);
        timeView.setBackground(rounded(0xFFFFF1E2, dp(22)));
        timeView.setPadding(0, dp(14), 0, dp(14));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-1, -2);
        tl.topMargin = dp(14);
        v.addView(timeView, tl);
        TextView tapHint = text("Tap the time to change it", 12, INK_SOFT);
        tapHint.setGravity(Gravity.CENTER);
        tapHint.setPadding(0, dp(6), 0, 0);
        v.addView(tapHint);
        timeView.setOnClickListener(x -> {
            final MaterialTimePicker p = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .setHour(draft.hour)
                    .setMinute(draft.minute)
                    .setTitleText("When should it play?")
                    .build();
            p.addOnPositiveButtonClickListener(y -> {
                draft.hour = p.getHour();
                draft.minute = p.getMinute();
                timeView.setText(draft.timeText());
                bounce(timeView);
            });
            p.show(getSupportFragmentManager(), "time");
        });

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        ChipGroup presets = new ChipGroup(this);
        presets.setSingleLine(true);
        Object[][] ps = {
                {E_DAWN + " 4:30 AM", 4, 30}, {E_SUNRISE + " 6:00 AM", 6, 0},
                {E_SUNSET + " 6:00 PM", 18, 0}, {E_MOON + " 9:00 PM", 21, 0}};
        for (final Object[] p : ps) {
            Chip c = new Chip(this);
            c.setText((String) p[0]);
            c.setChipBackgroundColor(ColorStateList.valueOf(CREAM));
            c.setTextColor(MAROON);
            c.setChipStrokeWidth(0);
            c.setOnClickListener(x -> {
                haptic(x);
                draft.hour = (Integer) p[1];
                draft.minute = (Integer) p[2];
                timeView.setText(draft.timeText());
                bounce(timeView);
            });
            presets.addView(c);
        }
        hs.addView(presets);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = dp(8);
        v.addView(hs, hl);

        v.addView(label("Name"));
        TextInputLayout nameBox = new TextInputLayout(this);
        nameBox.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        float cr = dp(16);
        nameBox.setBoxCornerRadii(cr, cr, cr, cr);
        nameBox.setHint("e.g. Evening Vishnu Sahasranamam");
        final TextInputEditText name = new TextInputEditText(nameBox.getContext());
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setText(draft.label);
        nameBox.addView(name);
        v.addView(nameBox);

        v.addView(label("Repeat on"));
        LinearLayout daysRow = row();
        int[] order = {1, 2, 3, 4, 5, 6, 0};
        String[] letters = {"M", "T", "W", "T", "F", "S", "S"};
        for (int k = 0; k < 7; k++) {
            final int idx = order[k];
            final TextView d = text(letters[k], 15, INK);
            d.setGravity(Gravity.CENTER);
            d.setTypeface(Typeface.DEFAULT_BOLD);
            styleDay(d, draft.days[idx]);
            d.setOnClickListener(x -> {
                haptic(x);
                draft.days[idx] = !draft.days[idx];
                styleDay(d, draft.days[idx]);
                bounce(d);
            });
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(0, dp(42), 1);
            dl.setMargins(dp(3), 0, dp(3), 0);
            daysRow.addView(d, dl);
        }
        v.addView(daysRow);

        v.addView(label("What to play"));
        final MaterialButtonToggleGroup modes = new MaterialButtonToggleGroup(this);
        modes.setSingleSelection(true);
        modes.setSelectionRequired(true);
        final MaterialButton mFiles = modeButton(E_NOTE + " File");
        final MaterialButton mApp = modeButton(E_PHONE + " App");
        final MaterialButton mLink = modeButton(E_LINK + " Link");
        modes.addView(mFiles, new LinearLayout.LayoutParams(0, -2, 1));
        modes.addView(mApp, new LinearLayout.LayoutParams(0, -2, 1));
        modes.addView(mLink, new LinearLayout.LayoutParams(0, -2, 1));
        v.addView(modes, new LinearLayout.LayoutParams(-1, -2));

        // -- audio files
        final LinearLayout filesBox = vertical();
        MaterialButton pick = softButton("Choose audio file(s)", R.drawable.ic_music);
        pick.setOnClickListener(x -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("audio/*");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(i, PICK_AUDIO);
        });
        filesBox.addView(pick, topGap(10));
        draftFiles = text("", 14, INK);
        draftFiles.setPadding(dp(4), dp(8), 0, 0);
        refreshDraftFiles();
        filesBox.addView(draftFiles);
        final MaterialSwitch shuffle = new MaterialSwitch(this);
        shuffle.setText("Shuffle the tracks");
        shuffle.setTextColor(INK);
        shuffle.setChecked(draft.shuffle);
        shuffle.setOnCheckedChangeListener((cb, on) -> draft.shuffle = on);
        filesBox.addView(shuffle, topGap(6));
        v.addView(filesBox);

        // -- music app
        final LinearLayout appBox = vertical();
        final MaterialButton appBtn = softButton(draft.appName.isEmpty() ? "Choose music app" : draft.appName, R.drawable.ic_music);
        appBtn.setOnClickListener(x -> pickApp(() -> appBtn.setText(draft.appName)));
        appBox.addView(appBtn, topGap(10));
        TextView appHint = text("The app opens at the set time and Play is pressed for you. "
                + "It plays whatever the app last had on, so leave it on your playlist.", 13, INK_SOFT);
        appHint.setPadding(dp(4), dp(8), 0, 0);
        appBox.addView(appHint);
        v.addView(appBox);

        // -- link
        final LinearLayout linkBox = vertical();
        TextInputLayout linkLayout = new TextInputLayout(this);
        linkLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        linkLayout.setBoxCornerRadii(cr, cr, cr, cr);
        linkLayout.setHint("Paste a song or playlist link");
        linkLayout.setHelperText("YouTube Music: Share → Copy link");
        final TextInputEditText linkField = new TextInputEditText(linkLayout.getContext());
        linkField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        linkField.setText(draft.link);
        linkLayout.addView(linkField);
        linkBox.addView(linkLayout, topGap(10));
        v.addView(linkBox);

        // -- pause timer (app / link)
        final LinearLayout stopBox = vertical();
        final TextView stopLabel = label("");
        stopBox.addView(stopLabel);
        Slider stopSlider = new Slider(this);
        stopSlider.setValueFrom(0);
        stopSlider.setValueTo(120);
        stopSlider.setStepSize(5);
        stopSlider.setValue(Math.min(120, Math.round(draft.stopAfterMin / 5f) * 5));
        stopSlider.setTrackActiveTintList(ColorStateList.valueOf(SAFFRON));
        stopSlider.setThumbTintList(ColorStateList.valueOf(SAFFRON));
        stopLabel.setText(stopText(draft.stopAfterMin));
        stopSlider.addOnChangeListener((sl, value, fromUser) -> {
            draft.stopAfterMin = Math.round(value);
            stopLabel.setText(stopText(draft.stopAfterMin));
        });
        stopBox.addView(stopSlider);
        v.addView(stopBox);

        // -- volume
        final TextView volLabel = label("Volume  ·  " + draft.volume + "%");
        v.addView(volLabel);
        Slider vol = new Slider(this);
        vol.setValueFrom(5);
        vol.setValueTo(100);
        vol.setStepSize(5);
        vol.setValue(Math.max(5, Math.min(100, Math.round(draft.volume / 5f) * 5)));
        vol.setTrackActiveTintList(ColorStateList.valueOf(SAFFRON));
        vol.setThumbTintList(ColorStateList.valueOf(SAFFRON));
        vol.addOnChangeListener((sl, value, fromUser) -> {
            draft.volume = Math.round(value);
            volLabel.setText("Volume  ·  " + draft.volume + "%");
        });
        v.addView(vol);

        final Runnable showMode = () -> {
            boolean f = Schedule.MODE_FILES.equals(draft.mode);
            boolean a = Schedule.MODE_APP.equals(draft.mode);
            boolean l = Schedule.MODE_LINK.equals(draft.mode);
            filesBox.setVisibility(f ? View.VISIBLE : View.GONE);
            appBox.setVisibility(a ? View.VISIBLE : View.GONE);
            linkBox.setVisibility(l ? View.VISIBLE : View.GONE);
            stopBox.setVisibility(f ? View.GONE : View.VISIBLE);
        };
        modes.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            if (id == mFiles.getId()) draft.mode = Schedule.MODE_FILES;
            else if (id == mApp.getId()) draft.mode = Schedule.MODE_APP;
            else draft.mode = Schedule.MODE_LINK;
            showMode.run();
        });
        modes.check(Schedule.MODE_APP.equals(draft.mode) ? mApp.getId()
                : Schedule.MODE_LINK.equals(draft.mode) ? mLink.getId() : mFiles.getId());
        showMode.run();

        // -- buttons
        LinearLayout btns = row();
        btns.setGravity(Gravity.CENTER_VERTICAL);
        btns.setPadding(0, dp(22), 0, 0);
        if (existing != null) {
            MaterialButton del = new MaterialButton(this);
            del.setText("Delete");
            del.setAllCaps(false);
            del.setTextColor(RED);
            del.setIconResource(R.drawable.ic_delete);
            del.setIconTint(ColorStateList.valueOf(RED));
            del.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            del.setElevation(0);
            del.setOnClickListener(x -> confirmDelete(existing, sheet));
            btns.addView(del);
        }
        btns.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        MaterialButton save = new MaterialButton(this);
        save.setText(existing == null ? "Save schedule" : "Save");
        save.setAllCaps(false);
        save.setTextSize(16);
        save.setTextColor(Color.WHITE);
        save.setBackgroundTintList(ColorStateList.valueOf(SAFFRON));
        save.setCornerRadius(dp(28));
        save.setPadding(dp(28), 0, dp(28), 0);
        btns.addView(save, new LinearLayout.LayoutParams(-2, dp(56)));
        v.addView(btns);

        save.setOnClickListener(x -> {
            draft.label = name.getText() == null ? "" : name.getText().toString().trim();
            draft.link = linkField.getText() == null ? "" : linkField.getText().toString().trim();
            if (Schedule.MODE_FILES.equals(draft.mode) && draft.uris.isEmpty()) {
                shake(pick);
                toast("Choose at least one audio file");
                return;
            }
            if (Schedule.MODE_APP.equals(draft.mode) && draft.appPackage.isEmpty()) {
                shake(appBtn);
                toast("Choose a music app");
                return;
            }
            if (Schedule.MODE_LINK.equals(draft.mode) && !draft.link.startsWith("http")) {
                shake(linkBox);
                toast("Paste a link starting with https://");
                return;
            }
            if (!draft.anyDay()) {
                shake(daysRow);
                toast("Pick at least one day");
                return;
            }
            haptic(x);
            Store.upsert(this, draft);
            Scheduler.schedule(this, draft);
            long next = Scheduler.nextTrigger(draft);
            final String title = draft.title();
            sheet.dismiss();
            render();
            if (draft.enabled && next > 0) {
                Snackbar.make(content, E_DIYA + "  " + title + " plays " + countdown(next - System.currentTimeMillis()),
                        Snackbar.LENGTH_LONG).setAnchorView(fab).show();
            }
        });

        ScrollView sc = new ScrollView(this);
        sc.addView(v);
        sheet.setContentView(sc);
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.show();
    }

    private String stopText(int min) {
        return min <= 0 ? "Pause after  ·  never (keep playing)" : "Pause after  ·  " + min + " minutes";
    }

    private MaterialButton modeButton(String t) {
        MaterialButton b = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setId(View.generateViewId());
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(MAROON);
        return b;
    }

    private MaterialButton softButton(String t, int icon) {
        MaterialButton b = new MaterialButton(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(MAROON);
        b.setIconResource(icon);
        b.setIconTint(ColorStateList.valueOf(MAROON));
        b.setBackgroundTintList(ColorStateList.valueOf(CREAM));
        b.setCornerRadius(dp(16));
        b.setElevation(0);
        return b;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_AUDIO || resultCode != RESULT_OK || data == null || draft == null) return;

        List<Uri> picked = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) picked.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            picked.add(data.getData());
        }
        if (picked.isEmpty()) return;

        draft.uris.clear();
        draft.names.clear();
        for (Uri u : picked) {
            try {
                // Keep permission to read this file forever, even after restarts.
                getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
            draft.uris.add(u.toString());
            draft.names.add(displayName(u));
        }
        refreshDraftFiles();
        if (draftFiles != null) bounce(draftFiles);
    }

    private void refreshDraftFiles() {
        if (draftFiles == null || draft == null) return;
        draftFiles.setText(draft.names.isEmpty() ? "No file chosen yet"
                : E_NOTE + " " + String.join("\n" + E_NOTE + " ", draft.names));
    }

    private String displayName(Uri u) {
        try (Cursor c = getContentResolver().query(u,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                if (n != null) return n;
            }
        } catch (Exception ignored) {
        }
        String last = u.getLastPathSegment();
        return last == null ? "Audio" : last;
    }

    // ------------------------------------------------------------------ speakers & apps

    private void chooseSpeaker() {
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
            return;
        }
        BluetoothAdapter ad = getSystemService(BluetoothManager.class).getAdapter();
        if (ad == null) {
            toast("This phone has no Bluetooth");
            return;
        }
        final List<String[]> items = new ArrayList<>(); // {name, address}
        try {
            Set<BluetoothDevice> bonded = ad.getBondedDevices();
            for (BluetoothDevice d : bonded) {
                String n = d.getName();
                items.add(new String[]{n == null ? d.getAddress() : n, d.getAddress()});
            }
        } catch (SecurityException e) {
            toast("Allow Bluetooth access first");
            return;
        }
        if (items.isEmpty()) {
            toast("No paired speakers. Pair your speaker in the phone's Bluetooth settings first.");
            return;
        }
        Collections.sort(items, (x, y) -> x[0].compareToIgnoreCase(y[0]));
        List<String[]> current = Store.speakers(this);
        String[] labels = new String[items.size()];
        final boolean[] checked = new boolean[items.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = items.get(i)[0];
            for (String[] c : current) {
                if (c[1].equalsIgnoreCase(items.get(i)[1]) || c[0].equals(items.get(i)[0])) checked[i] = true;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Which speakers can play?")
                .setMultiChoiceItems(labels, checked, (d, which, on) -> checked[which] = on)
                .setPositiveButton("Save", (d, w) -> {
                    List<String[]> chosen = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) chosen.add(items.get(i));
                    Store.setSpeakers(this, chosen);
                    render();
                    Snackbar.make(content, chosen.isEmpty() ? "Any Bluetooth speaker will be used"
                                    : "Waits up to 2 min for " + Store.speakerLabel(this) + ", then plays on the phone",
                            Snackbar.LENGTH_LONG).setAnchorView(fab).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_BT && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) chooseSpeaker();
        else render();
    }

    private void pickApp(Runnable done) {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(q, 0);
        final List<String[]> items = new ArrayList<>(); // {label, package}
        for (ResolveInfo r : apps) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            items.add(new String[]{String.valueOf(r.loadLabel(pm)), pkg});
        }
        Collections.sort(items, (x, y) -> x[0].compareToIgnoreCase(y[0]));
        String[] labels = new String[items.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = items.get(i)[0];
        new MaterialAlertDialogBuilder(this)
                .setTitle("Choose music app")
                .setItems(labels, (d, which) -> {
                    draft.appName = items.get(which)[0];
                    draft.appPackage = items.get(which)[1];
                    done.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------------ words & colours

    private String greeting(int h) {
        if (h >= 4 && h < 12) return "Suprabhatam · good morning";
        if (h >= 12 && h < 16) return "Namaste · good afternoon";
        if (h >= 16 && h < 20) return "Shubh Sandhya · good evening";
        return "Shubh Ratri · good night";
    }

    private String nextUpText() {
        String pid = PlaybackService.playingId;
        Schedule best = null;
        long bestT = Long.MAX_VALUE;
        for (Schedule s : Store.all(this)) {
            if (pid != null && s.id.equals(pid)) return E_NOTE + "  Playing " + s.title();
            if (!s.enabled || !s.hasSource()) continue;
            long t = Scheduler.nextTrigger(s);
            if (t > 0 && t < bestT) {
                bestT = t;
                best = s;
            }
        }
        if (best == null) return "Nothing scheduled yet";
        return "Next: " + best.title() + "  ·  " + countdown(bestT - System.currentTimeMillis());
    }

    private String countdown(long ms) {
        long sec = Math.max(0, ms / 1000);
        if (sec < 60) return "in " + sec + "s";
        long min = sec / 60;
        if (min < 60) return "in " + min + " min";
        long h = min / 60;
        if (h < 24) return "in " + h + "h " + (min % 60) + "m";
        return "in " + (h / 24) + "d " + (h % 24) + "h";
    }

    private String sourceLine(Schedule s) {
        if (Schedule.MODE_APP.equals(s.mode)) return E_PHONE + "  " + s.appName;
        if (Schedule.MODE_LINK.equals(s.mode)) {
            String l = s.link == null ? "" : s.link;
            return E_LINK + "  " + (l.contains("youtube") ? "YouTube link" : Uri.parse(l).getHost());
        }
        if (s.names.isEmpty()) return E_NOTE + "  No audio chosen";
        return E_NOTE + "  " + (s.names.size() == 1 ? s.names.get(0) : s.names.size() + " tracks"
                + (s.shuffle ? ", shuffled" : ""));
    }

    private String emoji(int h) {
        if (h >= 4 && h < 7) return E_SUNRISE;
        if (h >= 7 && h < 16) return E_SUN;
        if (h >= 16 && h < 20) return E_SUNSET;
        return E_MOON;
    }

    /** Colour pair per time of day: dawn, daytime, sandhya (evening), night. */
    private int[] accent(int h) {
        if (h >= 4 && h < 7) return new int[]{0xFFF97316, 0xFFFBBF24};
        if (h >= 7 && h < 16) return new int[]{0xFFD97706, 0xFFFACC15};
        if (h >= 16 && h < 20) return new int[]{0xFFDB2777, 0xFFF97316};
        return new int[]{0xFF4338CA, 0xFF7C3AED};
    }

    private void styleDay(TextView d, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(on ? SAFFRON : 0xFFF5EDE6);
        d.setBackground(g);
        d.setTextColor(on ? Color.WHITE : INK_SOFT);
    }

    // ------------------------------------------------------------------ motion

    private void loop(ObjectAnimator a, long ms) {
        a.setDuration(ms);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.start();
        running.add(a);
    }

    private void stopAnimations() {
        for (Animator a : running) a.cancel();
        running.clear();
    }

    /** Three little bars that dance while music plays. */
    private LinearLayout equalizer(int color, int heightDp) {
        LinearLayout eq = row();
        eq.setGravity(Gravity.BOTTOM);
        for (int i = 0; i < 3; i++) {
            View bar = new View(this);
            bar.setBackground(rounded(color, dp(2)));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(dp(3), dp(heightDp));
            bl.setMargins(dp(1), 0, dp(1), 0);
            eq.addView(bar, bl);
            bar.setPivotY(dp(heightDp));
            ObjectAnimator a = ObjectAnimator.ofFloat(bar, View.SCALE_Y, 0.25f, 1f);
            a.setRepeatMode(ValueAnimator.REVERSE);
            loop(a, 320 + rnd.nextInt(300));
        }
        return eq;
    }

    private void bounce(final View v) {
        v.animate().cancel();
        v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(90).withEndAction(() ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(260)
                        .setInterpolator(new OvershootInterpolator(3f)).start()).start();
    }

    private void shake(View v) {
        ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0, dp(10), -dp(10), dp(6), -dp(6), 0).setDuration(380).start();
    }

    private void haptic(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    // ------------------------------------------------------------------ small helpers

    private MaterialButton roundIcon(int icon, int color) {
        MaterialButton b = new MaterialButton(this);
        b.setIconResource(icon);
        b.setIconTint(ColorStateList.valueOf(Color.WHITE));
        b.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        b.setIconPadding(0);
        b.setIconSize(dp(22));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setCornerRadius(dp(23));
        b.setBackgroundTintList(ColorStateList.valueOf(color));
        return b;
    }

    private void tintOval(View v, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        v.setBackground(g);
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private LinearLayout.LayoutParams topGap(int gapDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(gapDp);
        return lp;
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private TextView label(String s) {
        TextView t = text(s, 13, INK_SOFT);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(dp(2), dp(18), 0, dp(8));
        return t;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
