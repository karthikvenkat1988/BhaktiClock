package com.bhakticlock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** One scheduled playback: a time, the days it repeats, and the audio files to play. */
public class Schedule {
    public String id = UUID.randomUUID().toString();
    public String label = "";
    public int hour = 18;
    public int minute = 0;
    /** Index 0 = Sunday ... 6 = Saturday (same as Calendar.DAY_OF_WEEK - 1). */
    public boolean[] days = {true, true, true, true, true, true, true};
    /** content:// URIs of the chosen audio files, played in order (or shuffled). */
    public List<String> uris = new ArrayList<>();
    public List<String> names = new ArrayList<>();
    public int volume = 70;
    public boolean shuffle = false;
    public boolean enabled = true;

    public int requestCode() {
        return id.hashCode();
    }

    public boolean anyDay() {
        for (boolean d : days) if (d) return true;
        return false;
    }

    public String title() {
        if (label != null && !label.trim().isEmpty()) return label.trim();
        if (!names.isEmpty()) return names.get(0);
        return "Music";
    }

    public String timeText() {
        int h12 = hour % 12 == 0 ? 12 : hour % 12;
        return String.format(Locale.US, "%d:%02d %s", h12, minute, hour < 12 ? "AM" : "PM");
    }

    public String daysText() {
        boolean all = true, weekdays = true, weekend = true;
        for (int i = 0; i < 7; i++) {
            if (!days[i]) all = false;
            boolean isWeekend = (i == 0 || i == 6);
            if (days[i] != !isWeekend) weekdays = false;
            if (days[i] != isWeekend) weekend = false;
        }
        if (all) return "Every day";
        if (weekdays) return "Weekdays";
        if (weekend) return "Weekends";
        String[] n = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        int[] order = {1, 2, 3, 4, 5, 6, 0};
        StringBuilder sb = new StringBuilder();
        for (int i : order) {
            if (days[i]) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(n[i]);
            }
        }
        return sb.length() == 0 ? "No days selected" : sb.toString();
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("label", label);
            o.put("hour", hour);
            o.put("minute", minute);
            JSONArray d = new JSONArray();
            for (boolean b : days) d.put(b);
            o.put("days", d);
            o.put("uris", new JSONArray(uris));
            o.put("names", new JSONArray(names));
            o.put("volume", volume);
            o.put("shuffle", shuffle);
            o.put("enabled", enabled);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return o;
    }

    public static Schedule fromJson(JSONObject o) {
        Schedule s = new Schedule();
        s.id = o.optString("id", s.id);
        s.label = o.optString("label", "");
        s.hour = o.optInt("hour", 18);
        s.minute = o.optInt("minute", 0);
        JSONArray d = o.optJSONArray("days");
        if (d != null) {
            for (int i = 0; i < 7 && i < d.length(); i++) s.days[i] = d.optBoolean(i, true);
        }
        JSONArray u = o.optJSONArray("uris");
        if (u != null) for (int i = 0; i < u.length(); i++) s.uris.add(u.optString(i));
        JSONArray n = o.optJSONArray("names");
        if (n != null) for (int i = 0; i < n.length(); i++) s.names.add(n.optString(i));
        s.volume = o.optInt("volume", 70);
        s.shuffle = o.optBoolean("shuffle", false);
        s.enabled = o.optBoolean("enabled", true);
        return s;
    }

    public Schedule copy() {
        return fromJson(toJson());
    }
}
