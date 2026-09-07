package com.hilal.ibadet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.os.Build;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class PrayerStatusReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "hilal_prayer_status_v4";
    private static final int NOTIFICATION_ID = 74124;
    private static final String STORE = "hilal_prayer_status_v1";

    @Override public void onReceive(Context context, Intent intent) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Hilâl • Namaz Vakti", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Sıradaki namaz ve kalan süre");
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(channel);
        }

        String raw = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString("times", "");
        if (raw.isEmpty()) return;
        try {
            JSONObject d = new JSONObject(raw);
            String date = d.optString("date", "");
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            if (!today.equals(date)) {
                PrayerStatusScheduler.scheduleNext(context, 1000L);
                return;
            }

            String[] keys = {"Imsak", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
            String[] names = {"İmsak", "Güneş", "Öğle", "İkindi", "Akşam", "Yatsı"};
            long now = System.currentTimeMillis();
            Calendar cal = Calendar.getInstance();
            long[] times = new long[keys.length];
            for (int i = 0; i < keys.length; i++) {
                String clock = d.optString(keys[i], "");
                if (!clock.matches("\\d{1,2}:\\d{2}")) { times[i] = -1L; continue; }
                String[] hm = clock.split(":");
                cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0]));
                cal.set(Calendar.MINUTE, Integer.parseInt(hm[1]));
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                times[i] = cal.getTimeInMillis();
            }

            long fajr = times[0], sunrise = times[1], dhuhr = times[2], asr = times[3], maghrib = times[4], isha = times[5];
            if (fajr < 0 || sunrise < 0 || dhuhr < 0 || asr < 0 || maghrib < 0 || isha < 0) {
                PrayerStatusScheduler.scheduleNext(context, 1000L);
                return;
            }

            long sunriseKerahatEnd = sunrise + 45L * 60L * 1000L;
            long zawalKerahatStart = dhuhr - 10L * 60L * 1000L;
            long eveningKerahatStart = maghrib - 45L * 60L * 1000L;
            long zawalNoticeStart = zawalKerahatStart - 10L * 60L * 1000L;
            long eveningNoticeStart = eveningKerahatStart - 10L * 60L * 1000L;

            String titleText;
            String remainingText;
            long end;

            // Sabit üst bildirimde uygulamadaki kerâhat zamanlamasıyla aynı sınırlar kullanılır.
            if (now >= sunrise && now < sunriseKerahatEnd) {
                titleText = "Kerâhat";
                end = sunriseKerahatEnd;
            } else if (now >= zawalKerahatStart && now < dhuhr) {
                titleText = "Kerâhat";
                end = dhuhr;
            } else if (now >= eveningKerahatStart && now < maghrib) {
                titleText = "Kerâhat";
                end = maghrib;
            } else if (now >= fajr && now < sunrise) {
                titleText = "Sabah • " + formatClock(fajr);
                end = sunrise;
            } else if (now >= sunriseKerahatEnd && now < zawalNoticeStart) {
                titleText = "Öğle • " + formatClock(dhuhr);
                end = dhuhr;
            } else if (now >= zawalNoticeStart && now < zawalKerahatStart) {
                titleText = "Kerâhat";
                end = zawalKerahatStart;
            } else if (now >= dhuhr && now < asr) {
                titleText = "Öğle • " + formatClock(dhuhr);
                end = asr;
            } else if (now >= asr && now < eveningNoticeStart) {
                titleText = "Akşam • " + formatClock(maghrib);
                end = maghrib;
            } else if (now >= eveningNoticeStart && now < eveningKerahatStart) {
                titleText = "Kerâhat";
                end = eveningKerahatStart;
            } else if (now >= maghrib && now < isha) {
                titleText = "Akşam • " + formatClock(maghrib);
                end = isha;
            } else if (now >= isha) {
                titleText = "Yatsı • " + formatClock(isha);
                Calendar tomorrowFajr = Calendar.getInstance();
                tomorrowFajr.setTimeInMillis(fajr);
                tomorrowFajr.add(Calendar.DAY_OF_YEAR, 1);
                end = tomorrowFajr.getTimeInMillis();
            } else {
                titleText = "İmsak • " + formatClock(fajr);
                end = fajr;
            }

            long remaining = Math.max(0L, end - now);
            // Bir sonraki saniyeye yukarı yuvarla; böylece gösterilen saniye gerçek zamana daha iyi oturur.
            long totalSeconds = (remaining + 999L) / 1000L;
            long hours = totalSeconds / 3600L;
            long minutes = (totalSeconds % 3600L) / 60L;
            long seconds = totalSeconds % 60L;
            remainingText = hours > 0
                    ? String.format(Locale.US, "%02d:%02d:%02d kaldı", hours, minutes, seconds)
                    : String.format(Locale.US, "%02d:%02d kaldı", minutes, seconds);

            Intent open = new Intent(context, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent content = PendingIntent.getActivity(context, NOTIFICATION_ID, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(context, CHANNEL_ID)
                    : new Notification.Builder(context);

            RemoteViews statusView = new RemoteViews(context.getPackageName(), R.layout.notification_prayer_status);
            statusView.setTextViewText(android.R.id.title, titleText);
            statusView.setTextViewText(android.R.id.text1, remainingText);
            statusView.setTextViewTextSize(android.R.id.title, android.util.TypedValue.COMPLEX_UNIT_SP, 17f);
            statusView.setTextViewTextSize(android.R.id.text1, android.util.TypedValue.COMPLEX_UNIT_SP, 20f);

            builder.setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(titleText)
                    .setContentText(remainingText)
                    .setCustomContentView(statusView)
                    .setCustomHeadsUpContentView(statusView)
                    .setContentIntent(content)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setColor(0xFF2E7D5A)
                    .setSound(null, null)
                    .setVibrate(new long[]{0});

            manager.notify(NOTIFICATION_ID, builder.build());

            // Her güncelleme bir sonraki tam saniyeye hizalanır; saniye göstergesi daha akıcı ve doğru ilerler.
            long untilNextSecond = 1000L - (System.currentTimeMillis() % 1000L);
            PrayerStatusScheduler.scheduleNext(context, Math.max(500L, untilNextSecond));
        } catch (Exception ignored) {
            PrayerStatusScheduler.scheduleNext(context, 5000L);
        }
    }

    private static String formatClock(long millis) {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(millis));
    }
}
