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
            if (!today.equals(date)) { PrayerStatusScheduler.scheduleNext(context, 1000L); return; }

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
            long fajr=times[0], sunrise=times[1], dhuhr=times[2], asr=times[3], maghrib=times[4], isha=times[5];
            if (fajr<0 || sunrise<0 || dhuhr<0 || asr<0 || maghrib<0 || isha<0) { PrayerStatusScheduler.scheduleNext(context,1000L); return; }

            long sunriseEnd=sunrise+45L*60L*1000L;
            long zawalStart=dhuhr-10L*60L*1000L;
            long eveningStart=maghrib-45L*60L*1000L;
            String title; long end;
            if (now>=sunrise && now<sunriseEnd) { title="Kerâhat"; end=sunriseEnd; }
            else if (now>=zawalStart && now<dhuhr) { title="Kerâhat"; end=dhuhr; }
            else if (now>=eveningStart && now<maghrib) { title="Kerâhat"; end=maghrib; }
            else if (now<fajr) { title="İmsak • "+formatClock(fajr); end=fajr; }
            else {
                long best=Long.MAX_VALUE; String bestName="";
                for(int i=0;i<times.length;i++){ if(times[i]>now && times[i]<best){best=times[i]; bestName=names[i];}}
                if(best==Long.MAX_VALUE){
                    Calendar tomorrow=Calendar.getInstance(); tomorrow.setTimeInMillis(fajr); tomorrow.add(Calendar.DAY_OF_YEAR,1);
                    best=tomorrow.getTimeInMillis(); bestName="İmsak";
                }
                title=bestName+" • "+formatClock(best); end=best;
            }
            long totalSeconds=Math.max(0L,(end-now+999L)/1000L);
            long h=totalSeconds/3600L, m=(totalSeconds%3600L)/60L, s=totalSeconds%60L;
            String remaining=h>0?String.format(Locale.US,"%02d:%02d:%02d kaldı",h,m,s):String.format(Locale.US,"%02d:%02d kaldı",m,s);

            Intent open=new Intent(context,MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent content=PendingIntent.getActivity(context,NOTIFICATION_ID,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,CHANNEL_ID):new Notification.Builder(context);
            RemoteViews view=new RemoteViews(context.getPackageName(),R.layout.notification_prayer_status);
            view.setTextViewText(android.R.id.title,title); view.setTextViewText(android.R.id.text1,remaining);
            view.setTextViewTextSize(android.R.id.title,android.util.TypedValue.COMPLEX_UNIT_SP,17f);
            view.setTextViewTextSize(android.R.id.text1,android.util.TypedValue.COMPLEX_UNIT_SP,20f);
            builder.setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(remaining)
                .setCustomContentView(view).setCustomHeadsUpContentView(view).setContentIntent(content)
                .setOngoing(true).setAutoCancel(false).setOnlyAlertOnce(true).setShowWhen(false)
                .setPriority(Notification.PRIORITY_HIGH).setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PUBLIC).setColor(0xFF2E7D5A)
                .setSound(null,null).setVibrate(new long[]{0});
            manager.notify(NOTIFICATION_ID,builder.build());
            long next=1000L-(System.currentTimeMillis()%1000L);
            PrayerStatusScheduler.scheduleNext(context,Math.max(500L,next));
        } catch(Exception ignored){ PrayerStatusScheduler.scheduleNext(context,5000L); }
    }
    private static String formatClock(long millis){ return new SimpleDateFormat("HH:mm",Locale.US).format(new Date(millis)); }
}
