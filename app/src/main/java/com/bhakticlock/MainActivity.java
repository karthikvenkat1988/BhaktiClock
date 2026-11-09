package com.bhakticlock;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int PICK_AUDIO = 42;

    private static final int SAFFRON = 0xFFC2410C;
    private static final int CARD_BG = 0xFFFFF7ED;
    private static final int WARN_BG = 0xFFFEF3C7;
    private static final int TEXT_DIM = 0xFF6B5E55;

    private LinearLayout root;

    // State for the schedule being edited in the dialog
    private Schedule draft;
    private TextView draftFiles;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        PlaybackService.ensureChannel(this);

        ScrollView sv = new ScrollView(this);
        sv.setFitsSystemWindows(true);
        sv.setBackgroundColor(Color.WHITE);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(24));
        sv.addView(root);
        setContentView(sv);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Scheduler.rescheduleAll(this);
        render();
    }

    // ---------------------------------------------------------------- main screen

    private void render() {
        root.removeAllViews();

        TextView sub = text("Plays your chosen music at set times, every day, on its own.", 14, TEXT_DIM);
        root.addView(sub);

        addWarnings();

        boolean bt = PlaybackService.isBluetoothAudioConnected(this);
        String spName = Store.speakerLabel(this);
        String which = spName.isEmpty() ? "Bluetooth speaker" : spName;
        TextView btLine = text((bt ? "\u25CF " + which + " connected" : "\u25CB " + which + " not connected right now"),
                14, bt ? 0xFF15803D : 0xFFB91C1C);
        btLine.setPadding(0, dp(12), 0, dp(4));
        root.addView(btLine);

        Button speakerBtn = button("Speakers: " + (spName.isEmpty() ? "any Bluetooth speaker" : spName) + "  (change)");
        speakerBtn.setOnClickListener(v -> chooseSpeaker());
        root.addView(speakerBtn);

        LinearLayout actions = row();
        Button add = button("+ Add schedule");
        add.setOnClickListener(v -> showEditor(null));
        Button stop = button("Stop music");
        stop.setOnClickListener(v -> startService(
                new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_STOP)));
        actions.addView(add, weighted());
        actions.addView(stop, weighted());
        root.addView(actions);

        List<Schedule> list = Store.all(this);
        if (list.isEmpty()) {
            TextView empty = text("No schedules yet.\n\nTap \"+ Add schedule\", pick a time (e.g. 6:00 PM), "
                    + "choose your MP3 (e.g. M.S. Subbulakshmi – Vishnu Sahasranamam) and save.", 15, TEXT_DIM);
            empty.setPadding(0, dp(24), 0, 0);
            root.addView(empty);
        }
        for (Schedule s : list) root.addView(card(s));
    }

    private void addWarnings() {
        if (!Scheduler.canUseExact(this)) {
            root.addView(warning("Allow \"Alarms & reminders\" so music starts on the exact minute.", () ->
                    open(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName())))));
        }
        boolean needsOverlay = false;
        for (Schedule sc : Store.all(this)) if (!Schedule.MODE_FILES.equals(sc.mode)) needsOverlay = true;
        if (needsOverlay && !Settings.canDrawOverlays(this)) {
            root.addView(warning("Allow \"Display over other apps\" so the music app can open by itself at the set time.", () ->
                    open(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())))));
        }
        if (needsOverlay && !PlaybackService.notificationAccess(this)) {
            root.addView(warning("Allow \"Notification access\" so Play can be pressed in the music app for you.", () ->
                    open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
        }
        PowerManager pm = getSystemService(PowerManager.class);
        if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
            root.addView(warning("Turn off battery optimisation so Android never puts this app to sleep.", () ->
                    open(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName())))));
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            root.addView(warning("Allow notifications so you can see and stop what's playing.", () ->
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1)));
        }
    }

    private View card(Schedule s) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(12), dp(16), dp(12));
        c.setBackground(rounded(CARD_BG));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(12);
        c.setLayoutParams(lp);

        LinearLayout top = row();
        TextView time = text(s.timeText(), 30, s.enabled ? SAFFRON : TEXT_DIM);
        time.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(time, weighted());
        Switch sw = new Switch(this);
        sw.setChecked(s.enabled);
        sw.setOnCheckedChangeListener((v, on) -> {
            s.enabled = on;
            Store.upsert(this, s);
            Scheduler.schedule(this, s);
            root.post(this::render);
        });
        top.addView(sw);
        c.addView(top);

        TextView title = text(s.title(), 17, Color.BLACK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        c.addView(title);
        c.addView(text(s.daysText() + "  ·  Volume " + s.volume + "%" + (s.shuffle ? "  ·  Shuffle" : ""), 14, TEXT_DIM));

        TextView f = text(s.sourceText(), 14, Color.DKGRAY);
        f.setPadding(0, dp(6), 0, dp(6));
        c.addView(f);

        long next = Scheduler.nextTrigger(s);
        String nextText = !s.enabled ? "Off" : next < 0 ? "Not scheduled"
                : "Next: " + DateFormat.format("EEE d MMM, h:mm a", next);
        c.addView(text(nextText, 13, TEXT_DIM));

        LinearLayout btns = row();
        Button test = button("Play now");
        test.setOnClickListener(v -> startForegroundService(
                new Intent(this, PlaybackService.class).putExtra("id", s.id)));
        Button edit = button("Edit");
        edit.setOnClickListener(v -> showEditor(s));
        Button del = button("Delete");
        del.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage("Delete \"" + s.title() + "\" at " + s.timeText() + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    Scheduler.cancel(this, s);
                    Store.remove(this, s.id);
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show());
        btns.addView(test, weighted());
        btns.addView(edit, weighted());
        btns.addView(del, weighted());
        c.addView(btns);
        return c;
    }

    // ---------------------------------------------------------------- editor dialog

    private void showEditor(Schedule existing) {
        draft = existing == null ? new Schedule() : existing.copy();

        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(20), dp(8), dp(20), dp(8));

        v.addView(label("Name (optional)"));
        final EditText name = new EditText(this);
        name.setHint("e.g. Evening Vishnu Sahasranamam");
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setText(draft.label);
        v.addView(name);

        v.addView(label("Time"));
        final Button timeBtn = button(draft.timeText());
        timeBtn.setTextSize(22);
        timeBtn.setOnClickListener(x -> new TimePickerDialog(this, (tp, h, m) -> {
            draft.hour = h;
            draft.minute = m;
            timeBtn.setText(draft.timeText());
        }, draft.hour, draft.minute, false).show());
        v.addView(timeBtn);

        v.addView(label("Repeat on"));
        LinearLayout daysRow = row();
        int[] order = {1, 2, 3, 4, 5, 6, 0};
        String[] letters = {"M", "T", "W", "T", "F", "S", "S"};
        for (int k = 0; k < 7; k++) {
            final int idx = order[k];
            final TextView d = new TextView(this);
            d.setText(letters[k]);
            d.setGravity(Gravity.CENTER);
            d.setTextSize(16);
            styleDay(d, draft.days[idx]);
            d.setOnClickListener(x -> {
                draft.days[idx] = !draft.days[idx];
                styleDay(d, draft.days[idx]);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1);
            lp.setMargins(dp(2), 0, dp(2), 0);
            daysRow.addView(d, lp);
        }
        v.addView(daysRow);

        v.addView(label("What to play"));
        final Button mFiles = button("Audio file");
        final Button mApp = button("Music app");
        final Button mLink = button("Link");
        LinearLayout modeRow = row();
        modeRow.addView(mFiles, weighted());
        modeRow.addView(mApp, weighted());
        modeRow.addView(mLink, weighted());
        v.addView(modeRow);

        // -- audio files
        final LinearLayout filesBox = new LinearLayout(this);
        filesBox.setOrientation(LinearLayout.VERTICAL);
        Button pick = button("Choose audio file(s)\u2026");
        pick.setOnClickListener(x -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("audio/*");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(i, PICK_AUDIO);
        });
        filesBox.addView(pick);
        draftFiles = text("", 14, Color.DKGRAY);
        refreshDraftFiles();
        filesBox.addView(draftFiles);
        v.addView(filesBox);

        // -- music app
        final LinearLayout appBox = new LinearLayout(this);
        appBox.setOrientation(LinearLayout.VERTICAL);
        final Button appBtn = button(draft.appName.isEmpty() ? "Choose music app\u2026" : draft.appName);
        appBtn.setOnClickListener(x -> pickApp(() -> appBtn.setText(draft.appName)));
        appBox.addView(appBtn);
        appBox.addView(text("At the set time the app opens and Play is pressed for you. "
                + "Works best when the app was last left on the song or playlist you want.", 13, TEXT_DIM));
        v.addView(appBox);

        // -- link
        final LinearLayout linkBox = new LinearLayout(this);
        linkBox.setOrientation(LinearLayout.VERTICAL);
        final EditText linkField = new EditText(this);
        linkField.setHint("Paste a song or playlist link");
        linkField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        linkField.setText(draft.link);
        linkBox.addView(linkField);
        linkBox.addView(text("In YouTube Music: Share \u2192 Copy link, then paste it here. "
                + "The link opens in the app and Play is pressed for you.", 13, TEXT_DIM));
        v.addView(linkBox);

        // -- stop timer (app/link)
        final LinearLayout stopBox = new LinearLayout(this);
        stopBox.setOrientation(LinearLayout.VERTICAL);
        stopBox.addView(label("Pause the music after (minutes). Leave empty to let it play on."));
        final EditText stopField = new EditText(this);
        stopField.setInputType(InputType.TYPE_CLASS_NUMBER);
        stopField.setHint("e.g. 30");
        if (draft.stopAfterMin > 0) stopField.setText(String.valueOf(draft.stopAfterMin));
        stopBox.addView(stopField);
        v.addView(stopBox);

        final Runnable showMode = () -> {
            boolean f = Schedule.MODE_FILES.equals(draft.mode);
            boolean a2 = Schedule.MODE_APP.equals(draft.mode);
            boolean l = Schedule.MODE_LINK.equals(draft.mode);
            filesBox.setVisibility(f ? View.VISIBLE : View.GONE);
            appBox.setVisibility(a2 ? View.VISIBLE : View.GONE);
            linkBox.setVisibility(l ? View.VISIBLE : View.GONE);
            stopBox.setVisibility(f ? View.GONE : View.VISIBLE);
            mFiles.setTypeface(null, f ? Typeface.BOLD : Typeface.NORMAL);
            mApp.setTypeface(null, a2 ? Typeface.BOLD : Typeface.NORMAL);
            mLink.setTypeface(null, l ? Typeface.BOLD : Typeface.NORMAL);
            mFiles.setTextColor(f ? SAFFRON : Color.DKGRAY);
            mApp.setTextColor(a2 ? SAFFRON : Color.DKGRAY);
            mLink.setTextColor(l ? SAFFRON : Color.DKGRAY);
        };
        mFiles.setOnClickListener(x -> { draft.mode = Schedule.MODE_FILES; showMode.run(); });
        mApp.setOnClickListener(x -> { draft.mode = Schedule.MODE_APP; showMode.run(); });
        mLink.setOnClickListener(x -> { draft.mode = Schedule.MODE_LINK; showMode.run(); });
        showMode.run();

        final TextView volLabel = label("Volume: " + draft.volume + "%");
        v.addView(volLabel);
        SeekBar vol = new SeekBar(this);
        vol.setMax(100);
        vol.setProgress(draft.volume);
        vol.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean user) {
                draft.volume = Math.max(1, p);
                volLabel.setText("Volume: " + draft.volume + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        v.addView(vol);

        CheckBox shuffle = new CheckBox(this);
        shuffle.setText("Shuffle (when several files are chosen)");
        shuffle.setChecked(draft.shuffle);
        shuffle.setOnCheckedChangeListener((cb, on) -> draft.shuffle = on);
        v.addView(shuffle);

        ScrollView sc = new ScrollView(this);
        sc.addView(v);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "New schedule" : "Edit schedule")
                .setView(sc)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(y -> {
            draft.link = linkField.getText().toString().trim();
            try {
                draft.stopAfterMin = Math.max(0, Integer.parseInt(stopField.getText().toString().trim()));
            } catch (NumberFormatException e) {
                draft.stopAfterMin = 0;
            }
            if (Schedule.MODE_FILES.equals(draft.mode) && draft.uris.isEmpty()) {
                toast("Choose at least one audio file");
                return;
            }
            if (Schedule.MODE_APP.equals(draft.mode) && draft.appPackage.isEmpty()) {
                toast("Choose a music app");
                return;
            }
            if (Schedule.MODE_LINK.equals(draft.mode) && !draft.link.startsWith("http")) {
                toast("Paste a link starting with https://");
                return;
            }
            if (!draft.anyDay()) {
                toast("Pick at least one day");
                return;
            }
            draft.label = name.getText().toString().trim();
            Store.upsert(this, draft);
            Scheduler.schedule(this, draft);
            long next = Scheduler.nextTrigger(draft);
            if (draft.enabled && next > 0) {
                toast("Will play next on " + DateFormat.format("EEE d MMM, h:mm a", next));
            }
            dialog.dismiss();
            render();
        }));
        dialog.show();
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
    }

    private void chooseSpeaker() {
        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 2);
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
        new AlertDialog.Builder(this)
                .setTitle("Which speakers can play?")
                .setMultiChoiceItems(labels, checked, (d, which, on) -> checked[which] = on)
                .setPositiveButton("Save", (d, w) -> {
                    List<String[]> chosen = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) chosen.add(items.get(i));
                    Store.setSpeakers(this, chosen);
                    toast(chosen.isEmpty() ? "Any Bluetooth speaker will be used"
                            : "Will wait up to 2 minutes for " + Store.speakerLabel(this) + ", then play on the phone");
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == 2 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) chooseSpeaker();
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
        new AlertDialog.Builder(this)
                .setTitle("Choose music app")
                .setItems(labels, (d, which) -> {
                    draft.appName = items.get(which)[0];
                    draft.appPackage = items.get(which)[1];
                    done.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refreshDraftFiles() {
        if (draftFiles == null || draft == null) return;
        draftFiles.setText(draft.names.isEmpty() ? "No file chosen yet"
                : "♪ " + String.join("\n♪ ", draft.names));
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

    // ---------------------------------------------------------------- small helpers

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private View warning(String msg, Runnable fix) {
        LinearLayout w = row();
        w.setGravity(Gravity.CENTER_VERTICAL);
        w.setPadding(dp(12), dp(8), dp(8), dp(8));
        w.setBackground(rounded(WARN_BG));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        w.setLayoutParams(lp);
        w.addView(text(msg, 14, 0xFF78350F), weighted());
        Button b = button("Fix");
        b.setOnClickListener(v -> fix.run());
        w.addView(b);
        return w;
    }

    private void styleDay(TextView d, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(on ? SAFFRON : 0xFFEDE7E1);
        d.setBackground(g);
        d.setTextColor(on ? Color.WHITE : TEXT_DIM);
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(14));
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
        TextView t = text(s, 13, TEXT_DIM);
        t.setPadding(0, dp(14), 0, dp(4));
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, -2, 1);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
