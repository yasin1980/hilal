package com.hilal.ibadet;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class PrayerStatusScheduler {
    private static final int REQUEST_CODE = 74125;
    private PrayerStatusScheduler() {}

    public static void scheduleNext(Context context, long delayMs) {
        try {
            AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarm == null) return;
            Intent intent = new Intent(context, PrayerStatusReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long when = System.currentTimeMillis() + Math.max(500L, delayMs);
            if (Build.VERSION.SDK_INT >= 23) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            else alarm.setExact(AlarmManager.RTC_WAKEUP, when, pi);
        } catch (Exception ignored) { }
    }

    public static void showNow(Context context) {
        try {
            context.sendBroadcast(new Intent(context, PrayerStatusReceiver.class));
        } catch (Exception ignored) { }
    }
}
