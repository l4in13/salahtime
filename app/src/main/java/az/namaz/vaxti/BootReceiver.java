package az.namaz.vaxti;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Telefon yenidən açılanda / saat dəyişəndə siqnalları bərpa edir. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Context app = context.getApplicationContext();
        String cached = PrayerRepo.loadCached(app);
        if (cached != null) PrayerScheduler.scheduleAll(app, cached);
        PrayerAlarmReceiver.refreshAsync(app, goAsync());
    }
}
