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
