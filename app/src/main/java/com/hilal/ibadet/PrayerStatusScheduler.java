package com.hilal.ibadet;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

final class PrayerStatusScheduler {
    private static final String STORE = "hilal_prayer_status_v1";
    private static final String KEY = "times";
    private static final int REQUEST = 74123;
    private PrayerStatusScheduler() {}
    static void update(Context context, String json) {
        try {
            org.json.JSONObject data = new org.json.JSONObject(json == null ? "{}" : json);
            data.put("updatedAt", System.currentTimeMillis());
            context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putString(KEY, data.toString()).apply();
            showNow(context);
            scheduleNext(context, 60000L);
        } catch (Exception ignored) {}
    }
    static void showNow(Context context) {
        Intent i = new Intent(context, PrayerStatusReceiver.class);
        i.setAction("com.hilal.ibadet.PRAYER_STATUS_UPDATE");
        context.sendBroadcast(i);
    }
    static void scheduleNext(Context context, long delayMs) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        Intent intent = new Intent(context, PrayerStatusReceiver.class);
        intent.setAction("com.hilal.ibadet.PRAYER_STATUS_UPDATE");
        PendingIntent pi = PendingIntent.getBroadcast(context, REQUEST, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long when = System.currentTimeMillis() + Math.max(15000L, delayMs);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !alarm.canScheduleExactAlarms()) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            else alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        } catch (SecurityException e) { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi); }
    }
}
