package com.bhakticlock;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import java.util.List;

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
        TextView btLine = text((bt ? "● Bluetooth speaker connected" : "○ No Bluetooth speaker connected right now"),
                14, bt ? 0xFF15803D : 0xFFB91C1C);
        btLine.setPadding(0, dp(12), 0, dp(4));
        root.addView(btLine);

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

        String files = s.names.isEmpty() ? "No audio chosen" : String.join("\n", s.names);
        TextView f = text("♪ " + files.replace("\n", "\n♪ "), 14, Color.DKGRAY);
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

        v.addView(label("Music"));
        Button pick = button("Choose audio file(s)…");
        pick.setOnClickListener(x -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("audio/*");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(i, PICK_AUDIO);
        });
        v.addView(pick);
        draftFiles = text("", 14, Color.DKGRAY);
        refreshDraftFiles();
        v.addView(draftFiles);

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
            if (draft.uris.isEmpty()) {
                toast("Choose at least one audio file");
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
