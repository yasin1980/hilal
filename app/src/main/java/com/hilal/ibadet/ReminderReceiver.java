package com.hilal.ibadet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;

import java.io.File;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "hilal_reminders_v10";

    private static final long[] VIBRATION_PATTERN =
            new long[]{0, 300, 150, 300, 150, 500};

    @Override
    public void onReceive(Context context, Intent source) {

        final PendingResult result = goAsync();

        NotificationManager manager =
                (NotificationManager) context.getSystemService(
                        Context.NOTIFICATION_SERVICE
                );

        if (manager == null) {
            result.finish();
            return;
        }

        String id = source.getStringExtra("id");

        String title = source.getStringExtra("title");
        String body = source.getStringExtra("body");
        String soundPath = source.getStringExtra("soundPath");

        if (title == null) {
            title = "Hilâl Hatırlatıcı";
        }

        if (body == null) {
            body = "Hatırlatma zamanı";
        }

        /*
         * Bildirime tıklanınca uygulamayı aç.
         */
        Intent open = new Intent(context, MainActivity.class);

        open.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        open.putExtra("hilalReminderId", id);

        open.setData(
                Uri.parse(
                        "hilal://reminder/"
                                + Uri.encode(id == null ? "" : id)
                )
        );

        PendingIntent contentIntent =
                PendingIntent.getActivity(
                        context,
                        id == null ? 0 : id.hashCode(),
                        open,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        /*
         * Android 8 ve üzeri bildirim kanalı.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Hilâl Hatırlatıcıları",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            channel.setDescription(
                    "Vird, dua, ibadet ve ezan hatırlatmaları"
            );

            channel.enableVibration(true);

            channel.setVibrationPattern(
                    VIBRATION_PATTERN
            );

            channel.setLockscreenVisibility(
                    Notification.VISIBILITY_PUBLIC
            );

            /*
             * Ses MediaPlayer tarafından çalınacak.
             * Böylece çift ses oluşmaz.
             */
            channel.setSound(null, null);

            manager.createNotificationChannel(channel);
        }

        /*
         * Hilâl özel bildirim görünümü.
         */
        RemoteViews compact =
                new RemoteViews(
                        context.getPackageName(),
                        R.layout.notification_hilal
                );

        compact.setTextViewText(
                android.R.id.title,
                title
        );

        compact.setTextView
