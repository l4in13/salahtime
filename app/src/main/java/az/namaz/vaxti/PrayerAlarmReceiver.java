package az.namaz.vaxti;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class PrayerAlarmReceiver extends BroadcastReceiver {

    public static final String ACTION_PRAYER = "az.namaz.vaxti.PRAYER";
    public static final String ACTION_REFRESH = "az.namaz.vaxti.REFRESH";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_PRAYER.equals(action)) {
            Notifier.showPrayer(context,
                    intent.getStringExtra("key"),
                    intent.getStringExtra("time"),
                    intent.getStringExtra("city"));
        } else if (ACTION_REFRESH.equals(action)) {
            refreshAsync(context.getApplicationContext(), goAsync());
        }
    }

    /** Arxa planda ixlasla.com-dan yeniləyir və siqnalları yenidən qurur. */
    public static void refreshAsync(final Context ctx, final PendingResult pr) {
        new Thread(() -> {
            try {
                refreshNow(ctx);
            } finally {
                if (pr != null) pr.finish();
            }
        }).start();
    }

    public static void refreshNow(Context ctx) {
        try {
            String json = PrayerRepo.fetch(ctx);
            PrayerScheduler.scheduleAll(ctx, json);
        } catch (Exception e) {
            String cached = PrayerRepo.loadCached(ctx);
            if (cached != null) PrayerScheduler.scheduleAll(ctx, cached);
            PrayerScheduler.scheduleRetry(ctx);
        }
        PrayerScheduler.scheduleDailyRefresh(ctx);
    }
}
