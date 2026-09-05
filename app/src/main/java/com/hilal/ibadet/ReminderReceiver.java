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

    private static final String CHANNEL_ID = "hilal_reminders_v8";

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

        // Bildirime basıldığında MainActivity açılsın
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

        // Bildirim kanalı
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Hilâl Hatırlatıcıları",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            // Sesi MediaPlayer ile çalacağız.
            channel.setSound(null, null);

            // Titreşim açık
            channel.enableVibration(true);

            channel.setVibrationPattern(
                    new long[]{
                            0,
                            300,
                            150,
                            300,
                            150,
                            500
                    }
            );

            channel.setDescription(
                    "Vird, dua, ibadet ve ezan hatırlatmaları"
            );

            channel.setLockscreenVisibility(
                    Notification.VISIBILITY_PUBLIC
            );

            manager.createNotificationChannel(channel);
        }

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

        // Özel bildirim görünümü
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

        // Notification oluştur
        Notification.Builder note;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            note = new Notification.Builder(
                    context,
                    CHANNEL_ID
            );
        } else {
            note = new Notification.Builder(context);
        }

        note.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(safeTitle)
                .setContentText(safeBody)
                .setCustomContentView(compact)
                .setCustomBigContentView(expanded)
                .setCustomHeadsUpContentView(compact)
                .setContentIntent(content)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setVibrate(
                        new long[]{
                                0,
                                300,
                                150,
                                300,
                                150,
                                500
                        }
                )
                .setSound(null);

        // Uygulama içi bildirim
        boolean shownInsideApp =
                MainActivity.deliverForegroundReminder(
                        id,
                        safeTitle,
                        safeBody
                );

        // Sistem bildirimini göster
        try {

            if (!shownInsideApp) {

                manager.notify(
                        id == null ? 1 : id.hashCode(),
                        note.build()
                );
            }

        } catch (SecurityException ignored) {
            // Bildirim izni yoksa uygulama çökmeyecek.
        }

        // Hatırlatıcıyı yeniden planla
        ReminderScheduler.afterFire(
                context,
                source
        );

        // Ses kontrolü
        AudioManager audio =
                (AudioManager) context.getSystemService(
                        Context.AUDIO_SERVICE
                );

        boolean normalMode =
                audio != null
                        && audio.getRingerMode()
                        == AudioManager.RINGER_MODE_NORMAL;

        boolean notificationVolumeOn =
                audio != null
                        && audio.getStreamVolume(
                        AudioManager.STREAM_NOTIFICATION
                ) > 0;

        /*
         * Önceki hatadaki %50 kontrolü kaldırıldı.
         *
         * Normal modda ve bildirim sesi açık olduğu sürece
         * ses çalacak.
         */
        if (normalMode && notificationVolumeOn) {

            playSelectedSound(
                    context,
                    source.getStringExtra("soundPath"),
                    pendingResult
            );

        } else {

            pendingResult.finish();
        }
    }

    private void playSelectedSound(
            Context context,
            String soundPath,
            PendingResult pendingResult
    ) {

        MediaPlayer player = null;

        try {

            player = new MediaPlayer();

            AudioAttributes attributes =
                    new AudioAttributes.Builder()
                            .setUsage(
                                    AudioAttributes.USAGE_NOTIFICATION
                            )
                            .setContentType(
                                    AudioAttributes.CONTENT_TYPE_SONIFICATION
                            )
                            .build();

            player.setAudioAttributes(attributes);

            final MediaPlayer finalPlayer = player;

            AtomicBoolean finished =
                    new AtomicBoolean(false);

            Handler handler =
                    new Handler(Looper.getMainLooper());

            Runnable finish = () -> {

                if (!finished.compareAndSet(
                        false,
                        true
                )) {
                    return;
                }

                try {
                    if (finalPlayer.isPlaying()) {
                        finalPlayer.stop();
                    }
                } catch (Exception ignored) {
                }

                try {
                    finalPlayer.release();
                } catch (Exception ignored) {
                }

                pendingResult.finish();
            };

            // Özel ses dosyası varsa onu kullan
            if (soundPath != null
                    && !soundPath.trim().isEmpty()
                    && new File(soundPath).isFile()) {

                player.setDataSource(soundPath);

            } else {

                // Özel ses yoksa telefonun varsayılan bildirim sesi
                Uri defaultSound =
                        RingtoneManager.getDefaultUri(
                                RingtoneManager.TYPE_NOTIFICATION
                        );

                if (defaultSound == null) {
                    finish.run();
                    return;
                }

                player.setDataSource(
                        context,
                        defaultSound
                );
            }

            player.setOnCompletionListener(
                    mp -> {
                        handler.removeCallbacks(finish);
                        finish.run();
                    }
            );

            player.setOnErrorListener(
                    (mp, what, extra) -> {

                        handler.removeCallbacks(finish);
                        finish.run();

                        return true;
                    }
            );

            player.prepare();

            player.setVolume(1.0f, 1.0f);

            player.start();

            // En fazla 8 saniye açık kalsın
            handler.postDelayed(
                    finish,
                    8000L
            );

        } catch (Exception ignored) {

            try {
                if (player != null) {
                    player.release();
                }
            } catch (Exception ignoredAgain) {
            }

            pendingResult.finish();
        }
    }
}
