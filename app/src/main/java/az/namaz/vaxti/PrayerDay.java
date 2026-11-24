package az.namaz.vaxti;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bir günün namaz vaxtları (ixlasla.com API-sindən). */
public class PrayerDay {

    /** Ekranda göstərilən sıra. */
    public static final String[] ALL_KEYS = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    /** Bildiriş göndərilən vaxtlar (Günəş namaz deyil). */
    public static final String[] PRAYER_KEYS = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};

    public String date;
    public String city;
    public String country;
    public String timezone;
    public boolean stale;
    public final Map<String, String> timings = new LinkedHashMap<>();

    public static String azName(String key) {
        switch (key) {
            case "Fajr": return "Sübh";
            case "Sunrise": return "Günəş";
            case "Dhuhr": return "Zöhr";
            case "Asr": return "Əsr";
            case "Maghrib": return "Məğrib";
            case "Isha": return "İşa";
            default: return key;
        }
    }

    public ZoneId zone() {
        try {
            if (timezone != null && !timezone.isEmpty()) return ZoneId.of(timezone);
        } catch (Exception ignored) { }
        return ZoneId.of("Asia/Baku");
    }

    public LocalDate localDate() {
        try {
            return LocalDate.parse(date);
        } catch (Exception e) {
            return null;
        }
    }

    /** "HH:mm" vaxtını həmin günün tam tarix-saatına çevirir. */
    public ZonedDateTime at(String key) {
        String t = timings.get(key);
        LocalDate d = localDate();
        if (t == null || d == null || t.length() < 5) return null;
        try {
            LocalTime lt = LocalTime.parse(t.substring(0, 5));
            return d.atTime(lt).atZone(zone());
        } catch (Exception e) {
            return null;
        }
    }
}
