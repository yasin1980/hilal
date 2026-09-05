package com.hilal.ibadet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String SOUND_CHANNEL_ID =
            "hilal_reminders_v7_sound";

    private static final String VIBRATE_CHANNEL_ID =
            "hilal_reminders_v7_vibrate";

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

        Intent open =
                new Intent(context, MainActivity.class);

        open.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        open.putExtra(
                "hilalReminderId",
                id
        );

        open.setData(
                Uri.parse(
                        "hilal://reminder/"
                                + Uri.encode(
                                id == null ? "" : id
                        )
                )
        );

        PendingIntent content =
                PendingIntent.getActivity(
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
                ) == 0;

        String channelId =
                lowOrSilent
                        ? VIBRATE_CHANNEL_ID
                        : SOUND_CHANNEL_ID;

        /*
         * Android 8 ve üzeri bildirim kanalı
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            channelId,
                            lowOrSilent
                                    ? "Hilâl Hatırlatıcıları (Titreşim)"
                                    : "Hilâl Hatırlatıcıları",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            channel.setDescription(
                    "Vird, dua, ibadet ve ezan hatırlatmaları"
            );

            channel.setLockscreenVisibility(
                    Notification.VISIBILITY_PUBLIC
            );

            channel.setSound(null, null);

            channel.enableVibration(
                    lowOrSilent
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

            manager.createNotificationChannel(
                    channel
            );
        }

        /*
         * Normal Android sistem bildirimi.
         *
         * Özel RemoteViews kullanılmıyor.
         * Böylece R.layout / variable layout hatası oluşmaz.
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
                                        .bigText(
                                                safeBody
                                        )
                        )
                        .setContentIntent(
                                content
                        )
                        .setAutoCancel(
                                true
                        )
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
         * Uygulama açıksa Hilâl'in kendi uygulama içi
         * bildirimini göstermeyi deniyoruz.
         */
        boolean shownInsideApp =
                MainActivity.deliverForegroundReminder(
                        id,
                        safeTitle,
                        safeBody
                );

        /*
         * Uygulama açık değilse Android sistem bildirimi.
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

        } catch (SecurityException ignored) {
            /*
             * Android 13+ bildirim izni yoksa
             * uygulama çökmeyecek.
             */
        }

        /*
         * Hatırlatıcıyı tekrar planla.
         */
        ReminderScheduler.afterFire(
                context,
                source
        );

        /*
         * Ses / titreşim
         */
        if (lowOrSilent) {

            pendingResult.finish();

        } else {

            playSelectedSound(
                    context,
                    source.getStringExtra(
                            "soundPath"
                    ),
                    pendingResult
            );
        }
    }

    private void playSelectedSound(
            Context context,
            String soundPath,
            PendingResult pendingResult
    ) {

        try {

            MediaPlayer player =
                    new MediaPlayer();

            player.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(
                                    AudioAttributes.USAGE_NOTIFICATION_EVENT
                            )
                            .setContentType(
                                    AudioAttributes.CONTENT_TYPE_SONIFICATION
                            )
                            .build()
            );

            AtomicBoolean finished =
                    new AtomicBoolean(false);

            Handler handler =
                    new Handler(
                            Looper.getMainLooper()
                    );

            Runnable finish =
                    () -> {

                        if (!finished.compareAndSet(
                                false,
                                true
                        )) {
                            return;
                        }

                        try {

                            if (player.isPlaying()) {
                                player.stop();
                            }

                        } catch (Exception ignored) {
                        }

                        try {

                            player.release();

                        } catch (Exception ignored) {
                        }

                        pendingResult.finish();
                    };

            if (soundPath != null
                    && new File(soundPath).isFile()) {

                player.setDataSource(
                        soundPath
                );

            } else {

                player.setDataSource(
                        context,
                        RingtoneManager.getDefaultUri(
                                RingtoneManager.TYPE_NOTIFICATION
                        )
                );
            }

            player.setOnCompletionListener(
                    mp -> {

                        handler.removeCallbacks(
                                finish
                        );

                        finish.run();
                    }
            );

            player.setOnErrorListener(
                    (mp, what, extra) -> {

                        handler.removeCallbacks(
                                finish
                        );

                        finish.run();

                        return true;
                    }
            );

            player.prepare();

            player.start();

            handler.postDelayed(
                    finish,
                    8000L
            );

        } catch (Exception ignored) {

            pendingResult.finish();
        }
    }
}
