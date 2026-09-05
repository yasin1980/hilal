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
            "hilal_reminders_v6_sound";

    private static final String VIBRATE_CHANNEL_ID =
            "hilal_reminders_v6_vibrate";

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

        /*
         * Hatırlatıcı ID
         */
        String id =
                source.getStringExtra("id");

        /*
         * Bildirim başlığı ve mesajı
         */
        String title =
                source.getStringExtra("title");

        String body =
                source.getStringExtra("body");

        final String safeTitle =
                title == null
                        ? "Hilâl Hatırlatıcı"
                        : title;

        final String safeBody =
                body == null
                        ? "Hatırlatma zamanı"
                        : body;

        /*
         * Bildirime basıldığında MainActivity açılır.
         */
        Intent open =
                new Intent(
                        context,
                        MainActivity.class
                );

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

        /*
         * Telefonun ses durumunu kontrol et.
         */
        AudioManager audio =
                (AudioManager) context.getSystemService(
                        Context.AUDIO_SERVICE
                );

        boolean silentOrVibrate =
                audio == null
                        || audio.getRingerMode()
                        != AudioManager.RINGER_MODE_NORMAL;

        boolean notificationVolumeOff =
                audio != null
                        && audio.getStreamMaxVolume(
                        AudioManager.STREAM_NOTIFICATION
                ) > 0
                        && audio.getStreamVolume(
                        AudioManager.STREAM_NOTIFICATION
                ) == 0;

        boolean lowOrSilent =
                silentOrVibrate
                        || notificationVolumeOff;

        /*
         * Ses açık ise ses kanalı,
         * sessiz/titreşim ise titreşim kanalı.
         */
        String channelId =
                lowOrSilent
                        ? VIBRATE_CHANNEL_ID
                        : SOUND_CHANNEL_ID;

        /*
         * Android 8.0 ve üzeri bildirim kanalı.
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

            /*
             * Android'in kendi bildirim sesini kapatıyoruz.
             *
             * Sesi aşağıda MediaPlayer ile kendimiz
             * çalıyoruz. Böylece bildirim iki kere
             * ses çıkarmaz.
             */
            channel.setSound(
                    null,
                    null
            );

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
         * Android sistem bildirimi.
         *
         * RemoteViews / R.layout kullanılmıyor.
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
                        .setSound(
                                null
                        );

        /*
         * Sessiz/titreşim modunda titreşim.
         */
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
         * Uygulama açıksa Hilâl'in uygulama içindeki
         * bildirimini göster.
         *
         * true dönerse sistem bildirimini göstermiyoruz.
         */
        boolean shownInsideApp =
                MainActivity.deliverForegroundReminder(
                        id,
                        safeTitle,
                        safeBody
                );

        /*
         * Uygulama açık değilse Android sistem
         * bildirimi gösterilir.
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
             * Android 13+ bildirim izni verilmemişse
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
         * Telefon sessiz/titreşimdeyse ses çalma.
         */
        if (lowOrSilent) {

            pendingResult.finish();

        } else {

            /*
             * Ses açık.
             *
             * Önce kullanıcının seçtiği özel sesi,
             * yoksa telefonun varsayılan bildirim
             * sesini çal.
             */
            playSelectedSound(
                    context,
                    source.getStringExtra(
                            "soundPath"
                    ),
                    pendingResult
            );
        }
    }

    /*
     * Bildirim sesini çalar.
     */
    private void playSelectedSound(
            Context context,
            String soundPath,
            PendingResult pendingResult
    ) {

        MediaPlayer player = null;

        try {

            player =
                    new MediaPlayer();

            /*
             * Android'e bunun bir BİLDİRİM sesi
             * olduğunu açıkça bildiriyoruz.
             */
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
             * MediaPlayer'ın kendi ses seviyesini
             * kısmıyoruz.
             *
             * Telefonun bildirim ses seviyesi geçerli
             * olmaya devam eder.
             */
            player.setVolume(
                    1.0f,
                    1.0f
            );

            final MediaPlayer finalPlayer =
                    player;

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
             * Kullanıcının seçtiği özel ses varsa
             * onu kullan.
             */
            if (soundPath != null
                    && !soundPath.trim().isEmpty()
                    && new File(soundPath).isFile()) {

                finalPlayer.setDataSource(
                        soundPath
                );

            } else {

                /*
                 * Özel ses yoksa telefonun varsayılan
                 * bildirim sesini kullan.
                 */
                Uri defaultSound =
                        RingtoneManager.getDefaultUri(
                                RingtoneManager.TYPE_NOTIFICATION
                        );

                if (defaultSound == null) {

                    finish.run();

                    return;
                }

                finalPlayer.setDataSource(
                        context,
                        defaultSound
                );
            }

            /*
             * Ses normal şekilde tamamlanırsa
             * receiver'ı kapat.
             */
            finalPlayer.setOnCompletionListener(
                    mp -> {

                        handler.removeCallbacks(
                                finish
                        );

                        finish.run();
                    }
            );

            /*
             * Ses çalarken hata oluşursa
             * receiver'ı kapat.
             */
            finalPlayer.setOnErrorListener(
                    (mp, what, extra) -> {

                        handler.removeCallbacks(
                                finish
                        );

                        finish.run();

                        return true;
                    }
            );

            /*
             * MediaPlayer hazırla.
             */
            finalPlayer.prepare();

            /*
             * Sesi çal.
             */
            finalPlayer.start();

            /*
             * Maksimum 15 saniye bekle.
             *
             * Uzun ses dosyaları BroadcastReceiver'ı
             * sonsuza kadar açık bırakmasın.
             */
            handler.postDelayed(
                    finish,
                    15000L
            );

        } catch (Exception ignored) {

            try {

                if (player != null) {
                    player.release();
                }

            } catch (Exception ignored2) {
            }

            pendingResult.finish();
        }
    }
}Uri defaultSound =
                    RingtoneManager.getDefaultUri(
                            RingtoneManager.TYPE_NOTIFICATION
                    );

            if (defaultSound == null) {
                finish.run();
                return;
            }

            finalPlayer.setDataSource(
                    context,
                    defaultSound
            );
        }

        finalPlayer.setOnCompletionListener(
                mp -> {
                    handler.removeCallbacks(
                            finish
                    );
                    finish.run();
                }
        );

        finalPlayer.setOnErrorListener(
                (mp, what, extra) -> {
                    handler.removeCallbacks(
                            finish
                    );
                    finish.run();
                    return true;
                }
        );

        finalPlayer.prepare();

        finalPlayer.start();

        /*
         * Çok uzun seslerde BroadcastReceiver'ın
         * açık kalmasını engelle.
         */
        handler.postDelayed(
                finish,
                15000L
        );

    } catch (Exception ignored) {

        try {
            if (player != null) {
                player.release();
            }
        } catch (Exception ignored2) {
        }

        pendingResult.finish();
    }
    }

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
