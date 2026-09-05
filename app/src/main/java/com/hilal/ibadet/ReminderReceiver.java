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

    private static final String CHANNEL_ID = "hilal_reminders_v11";

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
         * Bildirime basınca uygulamayı aç.
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
         * Bildirim kanalı.
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
             * Sesi MediaPlayer çalacak.
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

        compact.setTextViewText(
                android.R.id.text1,
                body
        );

        RemoteViews expanded =
                new RemoteViews(
                        context.getPackageName(),
                        R.layout.notification_hilal
                );

        expanded.setTextViewText(
                android.R.id.title,
                title
        );

        expanded.setTextViewText(
                android.R.id.text1,
                body
        );

        /*
         * Sistem bildirimi.
         */
        Notification.Builder builder =
                new Notification.Builder(
                        context,
                        CHANNEL_ID
                );

        builder.setSmallIcon(
                R.drawable.ic_notification
        );

        builder.setContentTitle(title);
        builder.setContentText(body);

        builder.setCustomContentView(compact);
        builder.setCustomBigContentView(expanded);
        builder.setCustomHeadsUpContentView(compact);

        builder.setContentIntent(contentIntent);
        builder.setAutoCancel(true);

        builder.setPriority(
                Notification.PRIORITY_HIGH
        );

        builder.setCategory(
                Notification.CATEGORY_REMINDER
        );

        builder.setVisibility(
                Notification.VISIBILITY_PUBLIC
        );

        builder.setVibrate(
                VIBRATION_PATTERN
        );

        /*
         * Sistem sesi ikinci kez çalmasın.
         */
        builder.setSound(null);

        /*
         * Uygulama açıkken mevcut Hilâl bildirim sistemi.
         */
        boolean shownInsideApp =
                MainActivity.deliverForegroundReminder(
                        id,
                        title,
                        body
                );

        /*
         * Uygulama kapalıysa sistem bildirimini göster.
         */
        if (!shownInsideApp) {

            try {

                manager.notify(
                        id == null ? 1 : id.hashCode(),
                        builder.build()
                );

            } catch (SecurityException ignored) {
            }
        }

        /*
         * Sonraki hatırlatıcıyı planla.
         */
        ReminderScheduler.afterFire(
                context,
                source
        );

        /*
         * Uygulama açık veya kapalı fark etmeksizin
         * bildirim sesini çal.
         */
        playNotificationSound(
                context,
                soundPath,
                result
        );
    }

    private void playNotificationSound(
            Context context,
            String soundPath,
            PendingResult result
    ) {

        final MediaPlayer player =
                new MediaPlayer();

        final Handler handler =
                new Handler(
                        Looper.getMainLooper()
                );

        final boolean[] finished =
                new boolean[]{false};

        final Runnable finish =
                new Runnable() {

                    @Override
                    public void run() {

                        if (finished[0]) {
                            return;
                        }

                        finished[0] = true;

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

                        result.finish();
                    }
                };

        try {

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

            /*
             * Özel ses seçilmişse onu kullan.
             */
            if (soundPath != null
                    && !soundPath.isEmpty()
                    && new File(soundPath).isFile()) {

                player.setDataSource(soundPath);

            } else {

                /*
                 * Özel ses yoksa telefonun varsayılan
                 * bildirim sesini kullan.
                 */
                Uri notificationSound =
                        RingtoneManager.getDefaultUri(
                                RingtoneManager.TYPE_NOTIFICATION
                        );

                if (notificationSound == null) {
                    result.finish();
                    return;
                }

                player.setDataSource(
                        context,
                        notificationSound
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

            /*
             * SESİ BAŞLAT.
             */
            player.start();

            /*
             * Maksimum 8 saniye.
             */
            handler.postDelayed(
                    finish,
                    8000L
            );

        } catch (Exception ignored) {

            try {
                player.release();
            } catch (Exception ignored2) {
            }

            result.finish();
        }
    }
}
