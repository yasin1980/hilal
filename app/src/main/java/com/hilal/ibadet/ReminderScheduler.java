\
package com.nsp112.hilal;

import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.JSONObject;
import java.util.*;

public final class ReminderScheduler {
    private static final String PREF="hilal_native_reminders";

    static int code(String id){ return id==null?0:id.hashCode(); }

    public static void schedule(Context c,String json) {
        try {
            JSONObject o=new JSONObject(json);
            String id=o.optString("id", UUID.randomUUID().toString());
            long when=o.optLong("whenMs",0);
            if(when<=0) return;

            c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(id,json).apply();

            Intent i=new Intent(c,ReminderReceiver.class);
            i.putExtra("json",json);
            PendingIntent pi=PendingIntent.getBroadcast(c,code(id),i,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));

            AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
            if(Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms()){
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
            } else if(Build.VERSION.SDK_INT>=23){
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,when,pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP,when,pi);
            }
        } catch(Exception ignored){}
    }

    public static void cancel(Context c,String id) {
        Intent i=new Intent(c,ReminderReceiver.class);
        PendingIntent pi=PendingIntent.getBroadcast(c,code(id),i,
            PendingIntent.FLAG_NO_CREATE | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        if(pi!=null){
            ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(pi);
            pi.cancel();
        }
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().remove(id).apply();
    }

    public static void cancelPrefix(Context c,String prefix) {
        Map<String,?> all=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getAll();
        for(String id:new ArrayList<>(all.keySet())) if(id.startsWith(prefix)) cancel(c,id);
    }

    public static void restoreAll(Context c) {
        Map<String,?> all=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getAll();
        for(Object v:all.values()) if(v instanceof String) schedule(c,(String)v);
    }
}
