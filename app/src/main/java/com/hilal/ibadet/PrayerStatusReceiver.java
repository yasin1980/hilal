package com.hilal.ibadet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class PrayerStatusReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "hilal_prayer_status_v3";
    private static final int NOTIFICATION_ID = 74124;
    private static final String STORE = "hilal_prayer_status_v1";

    @Override public void onReceive(Context context, Intent intent) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Hilâl • Namaz Vakti", NotificationManager.IMPORTANCE_DEFAULT);
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
            if (!today.equals(date)) { PrayerStatusScheduler.scheduleNext(context, 60000L); return; }

            String[] keys = {"Imsak", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
            String[] names = {"İmsak", "Güneş", "Öğle", "İkindi", "Akşam", "Yatsı"};
            long now = System.currentTimeMillis(), best = Long.MAX_VALUE;
            String bestName = "", bestClock = "";
            Calendar cal = Calendar.getInstance();
            for (int i = 0; i < keys.length; i++) {
                String clock = d.optString(keys[i], "");
                if (!clock.matches("\\d{1,2}:\\d{2}")) continue;
                String[] hm = clock.split(":");
                cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0]));
                cal.set(Calendar.MINUTE, Integer.parseInt(hm[1]));
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long t = cal.getTimeInMillis();
                if (t > now && t < best) {
                    best = t;
                    bestName = names[i];
                    bestClock = String.format(Locale.US, "%02d:%02d", Integer.parseInt(hm[0]), Integer.parseInt(hm[1]));
                }
            }
            if (best == Long.MAX_VALUE) {
                bestName = "İmsak";
                bestClock = d.optString("Imsak", "");
                String[] hm = bestClock.split(":");
                if (hm.length == 2) {
                    cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0]));
                    cal.set(Calendar.MINUTE, Integer.parseInt(hm[1]));
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    best = cal.getTimeInMillis();
                }
            }
            if (bestName.isEmpty() || best == Long.MAX_VALUE) return;

            long sec = Math.max(0, best - now) / 1000;
            String countdown = String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60);
            String text = bestName + " • " + bestClock + "  |  " + countdown;

            Intent open = new Intent(context, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent content = PendingIntent.getActivity(context, NOTIFICATION_ID, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(context, CHANNEL_ID)
                    : new Notification.Builder(context);
            b.setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Hilâl • Sıradaki Vakit")
                    .setContentText(text)
                    .setContentIntent(content)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setColor(0xFF084331)
                    .setStyle(new Notification.BigTextStyle().bigText(text));
            manager.notify(NOTIFICATION_ID, b.build());
            PrayerStatusScheduler.scheduleNext(context, 60000L);
        } catch (Exception ignored) {}
    }
}
