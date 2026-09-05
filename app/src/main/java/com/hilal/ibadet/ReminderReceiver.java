package com.hilal.ibadet;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReminderReceiver extends BroadcastReceiver {

    /*
     * Yeni bildirim kanalı.
     * Eski kanaldaki kapalı ses/titreşim ayarlarından etkilenmemesi için
     * yeni bir kanal kullanıyoruz.
     */
    private static final String CHANNEL_ID = "hilal_reminders_v9";

    private static final long[] VIBRATION_PATTERN =
            new long[]{0, 300, 150, 300, 150, 500};

    @Override
    public void onReceive(Context context, Intent source) {

        final PendingResult pendingResult = goAsync();

        NotificationManager manager =
                (NotificationManager) context.getSystemService(
                        Context.NOTIFICATION_SERVICE
                );

        if (manager == null) {
            pendingResult.finish();
            return;
        }

        String id = source.getStringExtra("id");

        /*
         * Bildirime basılınca uygulamayı aç.
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

        PendingIntent content = PendingIntent.getActivity(
                context,
                id == null ? 0 : id.hashCode(),
                open,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        /*
         * Android 8+
         *
         * Titreşim açık.
         *
         * Bildirim kanalının kendi sesini kullanmıyoruz.
         * Sesi aşağıda MediaPlayer ile ayrıca çalıyoruz.
         */
        if (Build.VERSION.SDK_INT >= 26) {

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
             * Sistem kanal sesi kapalı.
             * Ses MediaPlayer tarafından bir kez çalınacak.
             */
            channel.setSound(null, null);

            manager.createNotificationChannel(channel);
        }

        String title =
                source.getStringExtra("title");

        String body =
                source.getStringExtra("body");

        String safeTitle =
                title == null
                        ? "Hilâl Hatırlatıcı"
                        : title;

        String safeBody =
                body == null
                        ? "Hatırlatma zamanı"
                        : body;

        /*
         * Hilâl özel bildirim görünümü.
         */
        android.widget.RemoteViews compact =
                new android.widget.RemoteViews(
                        context.getPackageName(),
                        R.layout.notification_hilal
                );

        compact.setTextViewText(
                android.R.id.title,
                safeTitle
        );

        compact.setTextViewText(
                android.R.id.text1,
                safeBody
        );

        android.widget.RemoteViews expanded =
                new android.widget.RemoteViews(
                        context.getPackageName(),
                        R.layout.notification_hilal
                );

        expanded.setTextViewText(
                android.R.id.title,
                safeTitle
        );

        expanded.setTextViewText(
                android.R.id.text1,
                safeBody
        );

        Notification.Builder note =
                new Notification.Builder(
                        context,
                        CHANNEL_ID
                )
                        .setSmallIcon(
                                R.drawable.ic_notification
                        )
                        .setContentTitle(
                                safeTitle
                        )
                        .setContentText(
                                safeBody
                        )
                        .setCustomContentView(
                                compact
                        )
                        .setCustomBigContentView(
                                expanded
                        )
                        .setCustomHeadsUpContentView(
                                compact
                        )
                        .setContentIntent(
                                content
                        )
                        .setAutoCancel(true)
                        .setPriority(
                                Notification.PRIORITY_HIGH
                        )
                        .setCategory(
                                Notification.CATEGORY_REMINDER
                        )
                        .setVisibility(
                                Notification.VISIBILITY_PUBLIC
                        )
                        .setVibrate(
                                VIBRATION_PATTERN
                        )
                        .setSound(null);

        /*
         * Uygulama açıksa Hilâl'in mevcut uygulama içi bildirim sistemi
         * çalışmaya devam ediyor.
         */
        boolean shownInsideApp =
                MainActivity.deliverForegroundReminder(
                        id,
                        safeTitle,
                        safeBody
                );

        try {

            /*
             * Uygulama kapalıysa Android sistem bildirimi gösterilir.
             */
            if (!shownInsideApp) {

                manager.notify(
                        id == null
                                ? 1
                                : id.hashCode(),
                        note.build()
                );
            }

        } catch (SecurityException ignored) {
            /*
             * Bildirim izni yoksa uygulama çö
