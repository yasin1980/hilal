package com.hilal.ibadet;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
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

    private static final String SOUND_CHANNEL_ID = "hilal_reminders_v5_sound";
    private static final String VIBRATE_CHANNEL_ID = "hilal_reminders_v5_vibrate";

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

        Intent open = new Intent(context, MainActivity.class);

        String id = source.getStringExtra("id");

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

        AudioManager audio =
                (AudioManager) context.getSystemService(
                        Context.AUDIO_SERVICE
                );

        boolean lowOrSilent =
                audio == null
                        || audio.getRingerMode()
                        != AudioManager.RINGER_MODE_NORMAL
                        || audio.getStreamMaxVolume(
                                AudioManager.STREAM_NOTIFICATION
                        ) == 0
                        || audio.getStreamVolume(
                                AudioManager.STREAM_NOTIFICATION
                        ) * 2
                        < audio.getStreamMaxVolume(
                                AudioManager.STREAM_NOTIFICATION
                        );

        String channelId =
                lowOrSilent
                        ? VIBRATE_CHANNEL_ID
                        : SOUND_CHANNEL_ID;

        /*
         * Android 8 ve üzeri bildirim kanalı
         */
        if (Build.VERSION.SDK_INT >= 26) {

            NotificationChannel channel =
                    new NotificationChannel(
                            channelId,
                            lowOrSilent
                                    ? "Hilâl Hatırlatıcıları (Titreşim)"
                                    : "Hilâl Hatırlatıcıları",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            /*
             * Sesi uygulamanın kendi MediaPlayer sistemi
             * üzerinden yönetiyoruz.
             */
            channel.setSound(null, null);

            channel.enableVibration(lowOrSilent);

            channel.setDescription(
                    "Vird, dua, ibadet ve ezan hatırlatmaları"
            );

            channel.setLockscreenVisibility(
                    Notification.VISIBILITY_PUBLIC
            );

            if (lowOrSilent) {
                channel.setVibrationPattern(
                        new long[]{
                                0,
                                260,
                                120,
                                260,
                                120,
                                360
                        }
                );
            }

            manager.createNotificationChannel(channel);
        }

        /*
         * Bildirim başlığı ve mesajı
         */
        String title = source.getStringExtra("title");
        String body = source.getStringExtra("body");

        String safeTitle =
                title == null
                        ? "Hilâl Hatırlatıcı"
                        : title;

        String safeBody =
                body == null
                        ? "Hatırlatma zamanı"
                        : body;

        /*
         * Android sistem bildirimi
         *
         * Özel RemoteViews / R.layout kullanmıyoruz.
         * Böylece R.layout / variable layout derleme hatası
         * oluşmuyor.
         */
        Notification.Builder note =
                new Notification.Builder(
                        context,
                        channelId
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
                        .setStyle(
                                new Notification.BigTextStyle()
                                        .bigText(safeBody)
                        )
                        .setContentIntent(content)
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
                        .setSound(null);

        if (lowOrSilent) {

            note.setVibrate(
                    new long[]{
                            0,
                            260,
                            120,
                            260,
                            120,
                            360
                    }
            );
        }

        /*
         * Uygulama açıksa MainActivity içerisindeki
         * Hilâl bildirim kartını gösterir.
         */
        boolean shownInsideApp =
                MainActivity.deliverForegroundReminder(
                        id,
                        safeTitle,
                        safeBody
                );

        /*
         * ÖNEMLİ:
         *
         * Uygulama açık ve aktifse Android'in üst bildirimini
         * göstermiyoruz.
         *
         * Uygulama kapalı / arka planda ise sistem bildirimi
         * gösteriliyor.
         */
        try {

            if (!shownInsideApp) {

                manager.notify(
                        id == null
                                ? 1
                                : id.hashCode(),
                        note.build()
                );
            }

        } catch (SecurityException denied) {

            /*
             * Android 13+ bildirim izni verilmemişse
             * uygulamanın çökmesini engelle.
             */
        }

        /*
         * Hatırlatıcı tekrar planlaması
         */
        ReminderScheduler.afterFire(
                context,
                source
        );

        /*
         * Sessiz / düşük ses durumunda sadece titreşim.
         */
        if (lowOrSilent) {

            pendingResult.finish();

        } else {

            /*
