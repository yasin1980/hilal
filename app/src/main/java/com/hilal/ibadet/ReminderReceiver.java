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
     * Yeni kanal kullanıyoruz.
     * Eski Android bildirim kanallarında ses ayarı kapalı kalmış olabilir.
     */
    private static final String CHANNEL_ID = "hilal_reminders_v8";

    private static final long[] VIBRATION_PATTERN =
            new long[]{0, 300, 150, 300, 150, 500};

    @Override
    public void onReceive(Context context, Intent source) {

        final PendingResult pendingResult = goAsync();

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager == null) {
            pendingResult.finish();
            return;
        }

        String id = source.getStringExtra("id");

        // Bildirime tıklanınca Hilâl uygulamasını aç
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
         * Android 8 ve üzeri:
         * Bildirim kanalı yüksek önem seviyesinde.
         * Titreşim DAİMA açık.
         *
         * Sesi MediaPlayer ile ayrıca çalıyoruz.
         * Böylece kullanıcının seçtiği soundPath varsa o ses,
         * yoksa telefonun varsayılan bildirim sesi kullanılır.
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
            channel.setVibrationPattern(VIBRATION_PATTERN);

            channel.setLockscreenVisibility(
                    Notification.VISIBILITY_PUBLIC
            );

            /*
             * Sesi burada vermiyoruz.
             * Çünkü MediaPlayer aşağıda seçilen sesi çalıyor.
             * Böylece aynı sesin iki kere çalmasını önlüyoruz.
             */
            channel.setSound(null, null);

            manager.createNotificationChannel(channel);
        }

        String title = source.getStringExtra("title");
        String body = source.getStringExtra("body");

        String safeTitle =
                title == null ? "Hilâl Hatırlatıcı" : title;

        String safeBody =
                body == null ? "Hatırlatma zamanı" : body;

        /*
         * Hilâl'in özel bildirim görünümü
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
                        .setSmallIcon(R.drawable.ic_notification)
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

                        // Titreşim DAİMA açık
                        .setVibrate(VIBRATION_PATTERN)

                        // Sistem tarafından ikinci kez ses çalınmasın
                        .setSound(null);

        /*
         * Uygulama açıkken Hilâl'in kendi bildirimini göster.
         * Kapalıyken Android sistem bildirimini göster.
         */
        boolean shownInsideApp =
                MainActivity.deliverForegroundReminder(
                        id,
                        safeTitle,
                        safeBody
                );

        try {

            if (!shownInsideApp) {
                manager.notify(
                        id == null ? 1 : id.hashCode(),
                        note.build()
                );
            }

        } catch (SecurityException denied) {
            // Bildirim izni yoksa uygulama çökmeyecek.
        }

        /*
         * Bir sonraki hatırlatmayı planla.
         */
        ReminderScheduler.afterFire(
                context,
                source
        );

        /*
         * SES
         *
         * soundPath varsa kullanıcının seçtiği ses.
         * Yoksa telefonun varsayılan bildirim sesi.
         */
        playSelectedSound(
                context,
                source.getStringExtra("soundPath"),
                pendingResult
        );
    }

    private void playSelectedSound(
            Context context,
            String soundPath,
            PendingResult pendingResult
    ) {

        MediaPlayer player = null;

        try {

            player = new MediaPlayer();

            player.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setUsage(
                                    AudioAttributes.USAGE_NOTIFICATION
                            )
                            .setContentType(
                                    AudioAttributes.CONTENT_TYPE_SONIFICATION
                            )
                            .build()
            );

            final MediaPlayer finalPlayer = player;

            AtomicBoolean finished =
                    new AtomicBoolean(false);

            Handler handler =
                    new Handler(Looper.getMainLooper());

            Runnable finish = () -> {

                if (!finished.compareAndSet(false, true)) {
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

            /*
             * Kullanıcının seçtiği özel ses varsa onu kullan.
             */
            if (soundPath != null
                    && new File(soundPath).isFile()) {

                player.setDataSource(soundPath);

            } else {

                /*
                 * Özel ses yoksa telefonun varsayılan
                 * bildirim sesini kullan.
                 */
                Uri defaultSound =
                        RingtoneManager.getDefaultUri(
                                RingtoneManager.TYPE_NOTIFICATION
                        );

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
            player.start();

            /*
             * Çok uzun bir ses BroadcastReceiver'ı açık bırakmasın.
             */
            handler.postDelayed(
                    finish,
                    8000L
            );

        } catch (Exception ignored) {

            if (player != null) {
                try {
                    player.release();
                } catch (Exception ignored2) {
                }
            }

            pendingResult.finish();
        }
    }
}
