package az.namaz.vaxti;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Məlumat YALNIZ https://ixlasla.com/api/prayer-times ünvanından götürülür. */
public class PrayerRepo {

    public static final String API_URL = "https://ixlasla.com/api/prayer-times";

    private static final String PREFS = "namaz";
    private static final String KEY_JSON = "json";
    private static final String KEY_SAVED_AT = "saved_at";

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Şəbəkədən çəkir, yoxlayır və yadda saxlayır. Uğursuz olsa exception atır. */
    public static String fetch(Context ctx) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API_URL).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "NamazVaxti-Android/1.0");
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new Exception("Server cavabı: " + code);
            StringBuilder sb = new StringBuilder();
            try (InputStream in = c.getInputStream();
                 BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            String json = sb.toString();
            if (parse(json).isEmpty()) throw new Exception("Cavab başa düşülmədi");
            prefs(ctx).edit()
                    .putString(KEY_JSON, json)
                    .putLong(KEY_SAVED_AT, System.currentTimeMillis())
                    .apply();
            return json;
        } finally {
            c.disconnect();
        }
    }

    public static String loadCached(Context ctx) {
        return prefs(ctx).getString(KEY_JSON, null);
    }

    public static long savedAt(Context ctx) {
        return prefs(ctx).getLong(KEY_SAVED_AT, 0L);
    }

    public static List<PrayerDay> parse(String json) {
        List<PrayerDay> out = new ArrayList<>();
        if (json == null) return out;
        try {
            JSONObject root = new JSONObject(json);
            for (String k : new String[]{"today", "tomorrow"}) {
                JSONObject d = root.optJSONObject(k);
                if (d == null) continue;
                JSONObject t = d.optJSONObject("timings");
                if (t == null) continue;
                PrayerDay day = new PrayerDay();
                day.date = d.optString("date", "");
                day.city = d.optString("city", "");
                day.country = d.optString("country", "");
                day.timezone = d.optString("timezone", "Asia/Baku");
                day.stale = d.optBoolean("stale", false);
                for (String key : PrayerDay.ALL_KEYS) {
                    String v = t.optString(key, null);
                    if (v != null && !v.isEmpty()) day.timings.put(key, v);
                }
                if (day.localDate() != null && !day.timings.isEmpty()) out.add(day);
            }
        } catch (Exception ignored) { }
        return out;
    }
}
