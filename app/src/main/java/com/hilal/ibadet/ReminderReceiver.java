package com.hilal.ibadet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "hilal_reminders_v13";

    private static final long[] VIBRATION_PATTERN =
            new long[]{0, 300, 150, 300, 150, 500};

    @Override
    public void onReceive(Context context, Intent source) {

        NotificationManager manager =
                (NotificationManager) context.getSystemService(
                        Context.NOTIFICATION_SERVICE
                );

        if (manager == null) {
            return;
        }

        String id = source.getStringExtra("id");

        String title = source.getStringExtra("title");
        String body = source.getStringExtra("body");

        if (title == null) {
            title = "Hilâl Hatırlatıcı";
        }

        if (body == null) {
            body = "Hatırlatma zamanı";
        }

        /*
         * Bildirime basınca Hilâl uygulamasını aç.
         */
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

        PendingIntent contentIntent =
                PendingIntent.getActivity(
                        context,
                        id == null ? 0 : id.hashCode(),
                        open,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        /*
         * Android 8+
         *
         * YENİ kanal oluşturuyoruz.
         * Ses + titreşim Android tarafından yönetilecek.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            Uri notificationSound =
                    RingtoneManager.getDefaultUri(
                            RingtoneManager.TYPE_NOTIFICATION
                    );

            AudioAttributes audioAttributes =
                    new AudioAttributes.Builder()
                            .setUsage(
                                    AudioAttributes.USAGE_NOTIFICATION
                            )
                            .setContentType(
                                    AudioAttributes.CONTENT_TYPE_SONIFICATION
                            )
                            .build();

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Hilâl Hatırlatıcıları",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            channel.setDescription(
                    "Vird, dua, ibadet ve ezan hatırlatmaları"
            );

            /*
             * GERÇEK BİLDİRİM SESİ
             */
            channel.setSound(
                    notificationSound,
                    audioAttributes
            );

            /*
             * TİTREŞİM
             */
            channel.enableVibration(true);

            channel.setVibrationPattern(
                    VIBRATION_PATTERN
            );

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

    /*
     * YENİ KANAL ID
     *
     * Önceki kanalın sessiz ayarlarını Android saklayabildiği için
     * yeni bir kanal kullanıyoruz.
     */
    private static final String CHANNEL_ID = "hilal_reminders_v8";

    @Override
    public void onReceive(Context context, Intent source) {

        final PendingResult pendingResult = goAsync();

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager == null) {
            pendingResult.finish();
            return;
        }

        // ---------------------------------------------------------
        // BİLDİRİME BASILDIĞINDA AÇILACAK EKRAN
        // ---------------------------------------------------------

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

        // ---------------------------------------------------------
        // BİLDİRİM KANALI
        // ---------------------------------------------------------

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Hilâl Hatırlatıcıları",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            /*
             * Sesi MediaPlayer ile kendimiz çalıyoruz.
             *
             * Böylece:
             * - Android kanal sesi + MediaPlayer sesi üst üste binmez.
             * - Seçilen özel ses dosyası kullanılabilir.
             * - Eski sessiz kanal ayarından etkilenmeyiz.
             */
            channel.setSound(null, null);

            // Titreşim kesin olarak açık.
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

        // ---------------------------------------------------------
        // BAŞLIK VE MESAJ
        // ---------------------------------------------------------

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

        // ---------------------------------------------------------
        // ÖZEL BİLDİRİM GÖRÜNÜMÜ
        // ---------------------------------------------------------

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

        // ---------------------------------------------------------
        // BİLDİRİM
        // ---------------------------------------------------------

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

                /*
                 * Android'in kendi bildirim sesi kullanılmıyor.
                 * Ses aşağıdaki MediaPlayer tarafından çalınacak.
                 */
                .setSound(null)

                /*
                 * TİTREŞİM HER ZAMAN AÇIK.
                 */
                .setVibrate(
                        new long[]{
                                0,
                                300,
                                150,
                                300,
                                150,
                                500
                        }
                );

        // ---------------------------------------------------------
        // UYGULAMA İÇİ BİLDİRİM
        // ---------------------------------------------------------

        boolean shownInsideApp =
                MainActivity.deliverForegroundReminder(
                        id,
                        safeTitle,
                        safeBody
                );

        // ---------------------------------------------------------
        // SİSTEM BİLDİRİMİNİ GÖSTER
        // ---------------------------------------------------------

        try {

            /*
             * Mevcut MainActivity davranışını değiştirmiyoruz.
             *
             * deliverForegroundReminder() false döndürüyorsa
             * Android sistem bildirimi gösterilir.
             */
            if (!shownInsideApp) {

                manager.notify(
                        id == null ? 1 : id.hashCode(),
                        note.build()
                );
            }

        } catch (SecurityException denied) {

            /*
             * Android 13+ bildirim izni verilmemişse
             * uygulama çökmeyecek.
             */

        }

        // ---------------------------------------------------------
        // HATIRLATMAYI YENİDEN PLANLA
        // ---------------------------------------------------------

        ReminderScheduler.afterFire(
                context,
                source
        );

        // ---------------------------------------------------------
        // SES
        // ---------------------------------------------------------

        AudioManager audio =
                (AudioManager) context.getSystemService(
                        Context.AUDIO_SERVICE
                );

        boolean normalMode =
                audio != null
                        && audio.getRingerMode()
                        == AudioManager.RINGER_MODE_NORMAL;

        boolean notificationVolumeAvailable =
                audio != null
                        && audio.getStreamVolume(
                        AudioManager.STREAM_NOTIFICATION
                ) > 0;

        /*
         * ÖNEMLİ:
         *
         * Önceki kodda bildirim sesi,
         * ses seviyesi %50'nin altındaysa tamamen kapatılıyordu.
         *
         * Bu yanlıştı.
         *
         * Artık:
         * - Ses %100 ise çalar
         * - Ses %50 ise çalar
         * - Ses %20 ise çalar
         * - Ses %1 ise çalar
         *
         * Sadece telefon gerçekten sessizdeyse veya
         * bildirim ses seviyesi 0 ise ses zorlanmaz.
         */

        if (normalMode && notificationVolumeAvailable) {

            playSelectedSound(
                    context,
                    source.getStringExtra("soundPath"),
                    pendingResult
            );

        } else {

            /*
             * Telefon sessizdeyse Android'in sessiz moduna
             * saygı gösteriyoruz.
             *
             * Bildirim/titreşim kısmı yine çalışır.
             */
            pendingResult.finish();
        }
    }

    // =============================================================
    // SESİ ÇAL
    // =============================================================

    private void playSelectedSound(
            Context context,
            String soundPath,
            PendingResult pendingResult
    ) {

        MediaPlayer player = null;

        try {

            player = new MediaPlayer();

            /*
             * Bildirim sesi olarak çalıştır.
             *
             * USAGE_NOTIFICATION kullanıyoruz.
             * Önceki USAGE_NOTIFICATION_EVENT yerine bu,
             * bildirim sesleri için daha doğru seçimdir.
             */
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

            // -----------------------------------------------------
            // ÖZEL SES DOSYASI VARSA ONU KULLAN
            // -----------------------------------------------------

            if (soundPath != null
                    && !soundPath.trim().isEmpty()
                    && new File(soundPath).isFile()) {

                player.setDataSource(soundPath);

            } else {

                // -------------------------------------------------
                // ÖZEL SES YOKSA TELEFONUN VARSAYILAN
                // BİLDİRİM SESİNİ KULLAN
                // -------------------------------------------------

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

            // -----------------------------------------------------
            // SESİ HAZIRLA
            // -----------------------------------------------------

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
             * Ses seviyesini MediaPlayer tarafından kısmıyoruz.
             * Telefonun bildirim ses seviyesi kullanılacak.
             */
            player.setVolume(1.0f, 1.0f);

            // -----------------------------------------------------
            // SESİ ÇAL
            // -----------------------------------------------------

            player.start();

            /*
             * Çok uzun özel ses dosyalarında Receiver'ın
             * sonsuza kadar açık kalmasını önle.
             *
             * Maksimum 8 saniye.
             */
            handler.postDelayed(
                    finish,
                    8000L
            );

        } catch (Exception ignored) {

            /*
             * Özel ses dosyası bozuksa uygulama çökmeyecek.
             *
             * Bildirim yine gösterilmiş olacak.
             */

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
