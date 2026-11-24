package az.namaz.vaxti;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

public class Notifier {

    public static final String CHANNEL_ID = "namaz_vaxti";

    public static void ensureChannel(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                "Namaz vaxtı", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Hər namaz vaxtı daxil olanda bildiriş");
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 400, 250, 400});
        nm.createNotificationChannel(ch);
    }

    public static boolean canNotify(Context ctx) {
        if (Build.VERSION.SDK_INT >= 33) {
            return ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static void showPrayer(Context ctx, String key, String time, String city) {
        if (key == null || !canNotify(ctx)) return;
        ensureChannel(ctx);

        Intent open = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = PrayerDay.azName(key) + " namazının vaxtıdır";
        String text = (time != null ? time : "")
                + (city != null && !city.isEmpty() ? " · " + city : "");

        Notification n = new Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();

        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(1000 + key.hashCode() % 1000, n);
    }
}
