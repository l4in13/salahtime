package az.namaz.vaxti;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/** Namaz vaxtları üçün dəqiq siqnalları (alarm) qurur. */
public class PrayerScheduler {

    private static final int RC_DAILY_REFRESH = 1;
    private static final int RC_RETRY = 2;

    /** Bu gün və sabah üçün hələ keçməmiş bütün namaz vaxtlarına siqnal qurur. */
    public static void scheduleAll(Context ctx, String json) {
        List<PrayerDay> days = PrayerRepo.parse(json);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long now = System.currentTimeMillis();

        for (PrayerDay d : days) {
            for (int i = 0; i < PrayerDay.PRAYER_KEYS.length; i++) {
                String key = PrayerDay.PRAYER_KEYS[i];
                ZonedDateTime t = d.at(key);
                if (t == null) continue;
                long ms = t.toInstant().toEpochMilli();
                if (ms <= now) continue;

                Intent it = new Intent(ctx, PrayerAlarmReceiver.class)
                        .setAction(PrayerAlarmReceiver.ACTION_PRAYER)
                        .putExtra("key", key)
                        .putExtra("time", d.timings.get(key))
                        .putExtra("city", d.city);
                int rc = (int) ((t.toLocalDate().toEpochDay() % 100000L) * 10L + i + 10);
                PendingIntent pi = PendingIntent.getBroadcast(ctx, rc, it,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                setExact(am, ms, pi);
            }
        }
    }

    /** Hər gecə 00:15-də yeni məlumatı ixlasla.com-dan çəkir. */
    public static void scheduleDailyRefresh(Context ctx) {
        ZonedDateTime next = ZonedDateTime.now(ZoneId.systemDefault())
                .toLocalDate().plusDays(1).atTime(0, 15)
                .atZone(ZoneId.systemDefault());
        setRefresh(ctx, RC_DAILY_REFRESH, next.toInstant().toEpochMilli());
    }

    /** İnternet olmayanda 30 dəqiqədən sonra yenidən cəhd. */
    public static void scheduleRetry(Context ctx) {
        setRefresh(ctx, RC_RETRY, System.currentTimeMillis() + 30L * 60L * 1000L);
    }

    private static void setRefresh(Context ctx, int rc, long ms) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent it = new Intent(ctx, PrayerAlarmReceiver.class)
                .setAction(PrayerAlarmReceiver.ACTION_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, rc, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi);
    }

    private static void setExact(AlarmManager am, long ms, PendingIntent pi) {
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi);
        }
    }
}
