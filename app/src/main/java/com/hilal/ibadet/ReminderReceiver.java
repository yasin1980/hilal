\
package com.hilal.ibadet;

import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.JSONObject;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String CH="hilal_reminders";

    @Override public void onReceive(Context c,Intent intent) {
        try {
            JSONObject o=new JSONObject(intent.getStringExtra("json"));
            String id=o.optString("id","hilal");
            String title=o.optString("title","Hilâl Hatırlatıcısı");
            String body=o.optString("body","Hatırlatma zamanı");
            long repeat=o.optLong("repeatMs",0);

            NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
            if(Build.VERSION.SDK_INT>=26){
                NotificationChannel ch=new NotificationChannel(CH,"Hilâl Hatırlatıcıları",NotificationManager.IMPORTANCE_HIGH);
                ch.enableVibration(true);
                nm.createNotificationChannel(ch);
            }

            Intent open=new Intent(c,MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent content=PendingIntent.getActivity(c,ReminderScheduler.code(id),open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));

            Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,CH):new Notification.Builder(c);
            b.setContentTitle(title).setContentText(body).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
             .setAutoCancel(true).setContentIntent(content).setVibrate(new long[]{0,250,150,250});
            if(Build.VERSION.SDK_INT<26) b.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
            nm.notify(ReminderScheduler.code(id),b.build());

            if(repeat>0){
                o.put("whenMs",System.currentTimeMillis()+repeat);
                ReminderScheduler.schedule(c,o.toString());
            } else {
                c.getSharedPreferences("hilal_native_reminders",Context.MODE_PRIVATE).edit().remove(id).apply();
            }
        } catch(Exception ignored){}
    }
}
