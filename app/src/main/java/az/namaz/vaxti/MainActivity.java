package az.namaz.vaxti;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MainActivity extends Activity {

    private static final int BG = Color.parseColor("#0F1115");
    private static final int CARD = Color.parseColor("#171A20");
    private static final int BORDER = Color.parseColor("#2A2E36");
    private static final int TEXT = Color.parseColor("#E6E8EB");
    private static final int MUTED = Color.parseColor("#8B919A");
    private static final int GREEN = Color.parseColor("#4ADE80");
    private static final int GREEN_BG = Color.parseColor("#1D3A2C");

    private static final String[] MONTHS = {"yanvar", "fevral", "mart", "aprel", "may", "iyun",
            "iyul", "avqust", "sentyabr", "oktyabr", "noyabr", "dekabr"};
    private static final String[] WEEKDAYS = {"bazar ertəsi", "çərşənbə axşamı", "çərşənbə",
            "cümə axşamı", "cümə", "şənbə", "bazar"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String json;

    private TextView titleTv, dateTv, nextTv, statusTv, warnTv;
    private LinearLayout rowsBox;
    private Button batteryBtn;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            render();
            handler.postDelayed(this, 30_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setContentView(buildUi());

        Notifier.ensureChannel(this);
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canNotify(this)) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        json = PrayerRepo.loadCached(this);
        render();
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.post(tick);
        updateBatteryButton();
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    // ---------- Məlumat ----------

    private void refresh() {
        statusTv.setText("ixlasla.com-dan yenilənir…");
        final Context app = getApplicationContext();
        new Thread(() -> {
            String result = null;
            String error = null;
            try {
                result = PrayerRepo.fetch(app);
                PrayerScheduler.scheduleAll(app, result);
            } catch (Exception e) {
                error = e.getMessage();
                String cached = PrayerRepo.loadCached(app);
                if (cached != null) PrayerScheduler.scheduleAll(app, cached);
                PrayerScheduler.scheduleRetry(app);
            }
            PrayerScheduler.scheduleDailyRefresh(app);
            final String r = result;
            final String err = error;
            runOnUiThread(() -> {
                if (r != null) json = r;
                render();
                if (err != null) {
                    statusTv.setText("Yeniləmək olmadı (internet?). Yaddaşdakı məlumat göstərilir.");
                }
            });
        }).start();
    }

    // ---------- Ekran ----------

    private void render() {
        List<PrayerDay> days = PrayerRepo.parse(json);
        rowsBox.removeAllViews();
        warnTv.setVisibility(View.GONE);

        if (days.isEmpty()) {
            titleTv.setText("Namaz vaxtları");
            dateTv.setText("Məlumat hələ yüklənməyib");
            nextTv.setText("");
            return;
        }

        ZoneId zone = days.get(0).zone();
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDate today = now.toLocalDate();

        PrayerDay shown = null;
        for (PrayerDay d : days) {
            if (today.equals(d.localDate())) { shown = d; break; }
        }
        if (shown == null) {
            shown = days.get(days.size() - 1);
            warnTv.setText("Diqqət: bugünkü məlumat yoxdur, internetə qoşulub \"Yenilə\" basın.");
            warnTv.setVisibility(View.VISIBLE);
        } else if (shown.stale) {
            warnTv.setText("Diqqət: sayt köhnə məlumat qaytarıb (stale).");
            warnTv.setVisibility(View.VISIBLE);
        }

        // Növbəti namaz
        PrayerDay nextDay = null;
        String nextKey = null;
        ZonedDateTime nextAt = null;
        outer:
        for (PrayerDay d : days) {
            for (String k : PrayerDay.PRAYER_KEYS) {
                ZonedDateTime t = d.at(k);
                if (t != null && t.isAfter(now)) {
                    nextDay = d; nextKey = k; nextAt = t;
                    break outer;
                }
            }
        }

        String city = shown.city.isEmpty() ? "Bakı" : shown.city;
        titleTv.setText(shown.country.isEmpty() ? city : city + ", " + shown.country);
        LocalDate sd = shown.localDate();
        dateTv.setText(sd.getDayOfMonth() + " " + MONTHS[sd.getMonthValue() - 1] + " "
                + sd.getYear() + " · " + WEEKDAYS[sd.getDayOfWeek().getValue() - 1]);

        for (String k : PrayerDay.ALL_KEYS) {
            String time = shown.timings.get(k);
            if (time == null) continue;
            boolean hl = shown == nextDay && k.equals(nextKey);
            rowsBox.addView(row(PrayerDay.azName(k), time, hl));
        }

        if (nextKey != null) {
            Duration left = Duration.between(now, nextAt);
            long h = left.toHours();
            long m = left.toMinutes() % 60;
            String leftStr = h > 0 ? h + " saat " + m + " dəq" : (m < 1 ? "1 dəqiqədən az" : m + " dəq");
            String which = nextDay == shown ? "" : " (sabah)";
            nextTv.setText("Növbəti: " + PrayerDay.azName(nextKey) + which + " · "
                    + nextDay.timings.get(nextKey) + "  —  " + leftStr + " qalıb");
        } else {
            nextTv.setText("Növbəti vaxt üçün yeniləmə gözlənilir");
        }

        long saved = PrayerRepo.savedAt(this);
        if (saved > 0) {
            String s = DateTimeFormatter.ofPattern("dd.MM HH:mm")
                    .format(Instant.ofEpochMilli(saved).atZone(ZoneId.systemDefault()));
            statusTv.setText("Mənbə: ixlasla.com · son yeniləmə " + s);
        }
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(36), dp(18), dp(24));
        scroll.addView(root);

        TextView header = text("NAMAZ VAXTLARI", 13, MUTED, false);
        header.setLetterSpacing(0.12f);
        header.setPadding(dp(4), 0, 0, dp(10));
        root.addView(header);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(CARD);
        cardBg.setCornerRadius(dp(14));
        cardBg.setStroke(dp(1), BORDER);
        card.setBackground(cardBg);
        card.setClipToOutline(true);
        root.addView(card);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(16), dp(16), dp(16), dp(14));
        titleTv = text("", 19, TEXT, true);
        dateTv = text("", 13, MUTED, false);
        dateTv.setPadding(0, dp(4), 0, 0);
        head.addView(titleTv);
        head.addView(dateTv);
        card.addView(head);
        card.addView(divider());

        rowsBox = new LinearLayout(this);
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        card.addView(rowsBox);

        nextTv = text("", 13, MUTED, false);
        nextTv.setPadding(dp(16), dp(12), dp(16), dp(14));
        card.addView(nextTv);

        warnTv = text("", 13, Color.parseColor("#FBBF24"), false);
        warnTv.setPadding(dp(4), dp(12), dp(4), 0);
        warnTv.setVisibility(View.GONE);
        root.addView(warnTv);

        statusTv = text("", 12, MUTED, false);
        statusTv.setPadding(dp(4), dp(14), dp(4), dp(8));
        root.addView(statusTv);

        Button refreshBtn = button("Yenilə");
        refreshBtn.setOnClickListener(v -> refresh());
        root.addView(refreshBtn);

        Button testBtn = button("Bildirişi yoxla");
        testBtn.setOnClickListener(v -> {
            if (!Notifier.canNotify(this)) {
                Toast.makeText(this, "Bildiriş icazəsi verilməyib", Toast.LENGTH_LONG).show();
                if (Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
                }
                return;
            }
            Notifier.showPrayer(this, "Dhuhr", "test", "Bildiriş işləyir");
        });
        root.addView(testBtn);

        batteryBtn = button("Batareya məhdudiyyətini söndür (tövsiyə)");
        batteryBtn.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        root.addView(batteryBtn);

        return scroll;
    }

    private void updateBatteryButton() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean ignoring = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        batteryBtn.setVisibility(ignoring ? View.GONE : View.VISIBLE);
    }

    private View row(String name, String time, boolean highlight) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), dp(14), dp(16), dp(14));
        if (highlight) r.setBackgroundColor(GREEN_BG);

        TextView n = text(name, 16, highlight ? GREEN : TEXT, highlight);
        n.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = text(time, 16, highlight ? GREEN : TEXT, true);
        r.addView(n);
        r.addView(t);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(r);
        wrap.addView(divider());
        return wrap;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(BORDER);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        return v;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(TEXT);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), BORDER);
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
