package com.hilal.ibadet;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONObject;
import java.util.Map;

final class ReminderScheduler {
    private static final String STORE = "hilal_native_reminders_v1";

    private ReminderScheduler() { }

    static boolean schedule(Context context, JSONObject data, boolean persist) {
        try {
            String id = data.optString("id", "hilal-reminder");
            long whenMs = data.optLong("whenMs", 0L);
            if (whenMs <= System.currentTimeMillis()) return false;

            Intent intent = new Intent(context, ReminderReceiver.class);
            intent.setAction("com.hilal.ibadet.REMINDER." + id);
            intent.putExtra("id", id);
            intent.putExtra("title", data.optString("title", "Hilâl Hatırlatıcı"));
            intent.putExtra("body", data.optString("body", "Hatırlatma zamanı"));
            intent.putExtra("repeatMs", data.optLong("repeatMs", 0L));
            intent.putExtra("sound", data.optString("sound", "phone"));
            intent.putExtra("soundPath", data.optString("soundPath", ""));

            PendingIntent pending = PendingIntent.getBroadcast(context, id.hashCode(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarm == null) return false;

            try {
                if (Build.VERSION.SDK_INT >= 31 && !alarm.canScheduleExactAlarms()) {
                    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pending);
                } else {
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pending);
                }
            } catch (SecurityException denied) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pending);
            }

            if (persist) preferences(context).edit().putString(id, data.toString()).apply();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    static void cancel(Context context, String id) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("com.hilal.ibadet.REMINDER." + id);
        PendingIntent pending = PendingIntent.getBroadcast(context, id.hashCode(), intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null && pending != null) alarm.cancel(pending);
        if (pending != null) pending.cancel();
        preferences(context).edit().remove(id).apply();
    }

    static void cancelPrefix(Context context, String prefix) {
        if (prefix == null || prefix.isEmpty()) return;
        for (String id : preferences(context).getAll().keySet()) {
            if (id.startsWith(prefix)) cancel(context, id);
        }
    }

    static void afterFire(Context context, Intent source) {
        String id = source.getStringExtra("id");
        if (id == null) return;
        long repeatMs = source.getLongExtra("repeatMs", 0L);
        if (repeatMs <= 0L) {
            preferences(context).edit().remove(id).apply();
            return;
        }
        try {
            String raw = preferences(context).getString(id, null);
            JSONObject data = raw == null ? new JSONObject() : new JSONObject(raw);
            data.put("id", id);
            data.put("title", source.getStringExtra("title"));
            data.put("body", source.getStringExtra("body"));
            data.put("repeatMs", repeatMs);
            data.put("sound", source.getStringExtra("sound"));
            data.put("soundPath", source.getStringExtra("soundPath"));
            long previous = Math.max(data.optLong("whenMs", 0L), System.currentTimeMillis());
            long next = previous + repeatMs;
            while (next <= System.currentTimeMillis() + 1000L) next += repeatMs;
            data.put("whenMs", next);
            schedule(context, data, true);
        } catch (Exception ignored) { }
    }

    static void restoreAll(Context context) {
        Map<String, ?> all = preferences(context).getAll();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            try {
                JSONObject data = new JSONObject(String.valueOf(entry.getValue()));
                long whenMs = data.optLong("whenMs", 0L);
                long repeatMs = data.optLong("repeatMs", 0L);
                if (whenMs <= now && repeatMs > 0L) {
                    while (whenMs <= now + 1000L) whenMs += repeatMs;
                    data.put("whenMs", whenMs);
                }
                if (whenMs > now) schedule(context, data, true);
                else preferences(context).edit().remove(entry.getKey()).apply();
            } catch (Exception ignored) {
                preferences(context).edit().remove(entry.getKey()).apply();
            }
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }
}
