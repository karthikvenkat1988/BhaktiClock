package com.bhakticlock;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** Saves schedules on the phone (survives restarts). */
public final class Store {
    private static final String PREFS = "bhakti_clock";
    private static final String KEY = "schedules";

    private Store() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized List<Schedule> all(Context c) {
        List<Schedule> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Schedule.fromJson(a.getJSONObject(i)));
        } catch (JSONException ignored) {
        }
        out.sort((x, y) -> Integer.compare(x.hour * 60 + x.minute, y.hour * 60 + y.minute));
        return out;
    }

    private static void saveAll(Context c, List<Schedule> list) {
        JSONArray a = new JSONArray();
        for (Schedule s : list) a.put(s.toJson());
        prefs(c).edit().putString(KEY, a.toString()).commit();
    }

    /** The speakers the user allowed, as {name, address} pairs. Empty = any Bluetooth speaker. */
    public static List<String[]> speakers(Context c) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("speakers", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONArray p = a.getJSONArray(i);
                out.add(new String[]{p.optString(0), p.optString(1)});
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    public static void setSpeakers(Context c, List<String[]> list) {
        JSONArray a = new JSONArray();
        for (String[] sp : list) {
            JSONArray p = new JSONArray();
            p.put(sp[0]);
            p.put(sp[1]);
            a.put(p);
        }
        prefs(c).edit().putString("speakers", a.toString()).commit();
    }

    public static boolean hasSpeaker(Context c) {
        return !speakers(c).isEmpty();
    }

    /** e.g. "Stone 350 Pro or Stone 352", or "" when any speaker is allowed. */
    public static String speakerLabel(Context c) {
        StringBuilder sb = new StringBuilder();
        for (String[] sp : speakers(c)) {
            if (sb.length() > 0) sb.append(" or ");
            sb.append(sp[0]);
        }
        return sb.toString();
    }

    public static synchronized Schedule find(Context c, String id) {
        if (id == null) return null;
        for (Schedule s : all(c)) if (s.id.equals(id)) return s;
        return null;
    }

    public static synchronized void upsert(Context c, Schedule s) {
        List<Schedule> list = all(c);
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(s.id)) {
                list.set(i, s);
                replaced = true;
                break;
            }
        }
        if (!replaced) list.add(s);
        saveAll(c, list);
    }

    public static synchronized void remove(Context c, String id) {
        List<Schedule> list = all(c);
        list.removeIf(s -> s.id.equals(id));
        saveAll(c, list);
    }
}
