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

    private static long timeToday(String clock, Calendar base) {
        if (clock == null || !clock.matches("\\d{1,2}:\\d{2}")) return Long.MAX_VALUE;
        try {
            String[] hm = clock.split(":");
            Calendar c = (Calendar) base.clone();
            c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0]));
            c.set(Calendar.MINUTE, Integer.parseInt(hm[1]));
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            return c.getTimeInMillis();
        } catch (Exception e) { return Long.MAX_VALUE; }
    }

    private static String countdown(long millis, String prefix) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) return String.format(Locale.US, "%s %02d:%02d:%02d", prefix, hours, minutes, seconds);
        return String.format(Locale.US, "%s %02d:%02d", prefix, minutes, seconds);
    }

    @Override public void onReceive(Context context, Intent intent) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Hilâl • Namaz Vakti", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Namaz vakti, kalan süre ve kerâhat durumu");
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
            Calendar nowCal = Calendar.getInstance();
            long now = System.currentTimeMillis();
            long best = Long.MAX_VALUE;
            String bestName = "";
            String bestClock = "";

            for (int i = 0; i < keys.length; i++) {
                String clock = d.optString(keys[i], "");
                long t = timeToday(clock, nowCal);
                if (t > now && t < best) {
                    best = t;
                    bestName = names[i];
                    String[] hm = clock.split(":");
                    bestClock = String.format(Locale.US, "%02d:%02d", Integer.parseInt(hm[0]), Integer.parseInt(hm[1]));
                }
            }

            if (best == Long.MAX_VALUE) {
                bestName = "İmsak";
                bestClock = d.optString("Imsak", "");
                long t = timeToday(bestClock, nowCal);
                if (t != Long.MAX_VALUE) {
                    Calendar tomorrow = (Calendar) nowCal.clone();
                    tomorrow.add(Calendar.DAY_OF_YEAR, 1);
                    String[] hm = bestClock.split(":");
                    tomorrow.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0]));
                    tomorrow.set(Calendar.MINUTE, Integer.parseInt(hm[1]));
                    tomorrow.set(Calendar.SECOND, 0);
                    tomorrow.set(Calendar.MILLISECOND, 0);
                    best = tomorrow.getTimeInMillis();
                }
            }
            if (bestName.isEmpty() || best == Long.MAX_VALUE) return;

            // Kerâhat: Güneş+45 dk, Öğle'den 10 dk önce başlayıp Öğle'ye kadar, Akşam'dan 45 dk önce başlayıp Akşam'a kadar.
            String kerahatText = "";
            boolean kerahat = false;
            long sunrise = timeToday(d.optString("Sunrise", ""), nowCal);
            long dhuhr = timeToday(d.optString("Dhuhr", ""), nowCal);
            long maghrib = timeToday(d.optString("Maghrib", ""), nowCal);
            long k1 = sunrise == Long.MAX_VALUE ? Long.MAX_VALUE : sunrise + 45L * 60L * 1000L;
            long k2s = dhuhr == Long.MAX_VALUE ? Long.MAX_VALUE : dhuhr - 10L * 60L * 1000L;
            long k2e = dhuhr;
            long k3s = maghrib == Long.MAX_VALUE ? Long.MAX_VALUE : maghrib - 45L * 60L * 1000L;
            long k3e = maghrib;
            long remaining = best - now;
            if (now >= sunrise && now < k1) {
                kerahat = true;
                kerahatText = countdown(k1 - now, "⚠️ Kerâhat • Bitmesine");
            } else if (now >= k2s && now < k2e) {
                kerahat = true;
                kerahatText = countdown(k2e - now, "⚠️ Kerâhat • Bitmesine");
            } else if (now >= k3s && now < k3e) {
                kerahat = true;
                kerahatText = countdown(k3e - now, "⚠️ Kerâhat • Bitmesine");
            }

            Intent open = new Intent(context, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent content = PendingIntent.getActivity(context, NOTIFICATION_ID, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(context, CHANNEL_ID)
                    : new Notification.Builder(context);

            String remainingText = countdown(remaining, "Vaktin Çıkmasına");
            String statusLine = bestName + " • " + bestClock + " • " + remainingText;
            RemoteViews statusView = new RemoteViews(context.getPackageName(), R.layout.notification_hilal);
            statusView.setTextViewText(android.R.id.title, "🕌 " + bestName + "  " + bestClock);
            statusView.setTextViewText(android.R.id.text1, kerahat ? (remainingText + "\n" + kerahatText) : remainingText);
            statusView.setTextViewTextSize(android.R.id.title, android.util.TypedValue.COMPLEX_UNIT_SP, 17f);
            statusView.setTextViewTextSize(android.R.id.text1, android.util.TypedValue.COMPLEX_UNIT_SP, 16f);
            
            builder.setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(statusLine)
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
                    .setColor(0xFF084331)
                    .setSound(null, null)
                    .setVibrate(new long[]{0});
            manager.notify(NOTIFICATION_ID, builder.build());
            PrayerStatusScheduler.scheduleNext(context, 1000L);
        } catch (Exception ignored) {
            PrayerStatusScheduler.scheduleNext(context, 5000L);
        }
    }
}
