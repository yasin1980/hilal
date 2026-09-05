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
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReminderReceiver extends BroadcastReceiver {

    /*
     * Yeni kanal isimleri kullanıyoruz.
     * Android 8+ eski kanal ayarlarını hafızada tuttuğu için
     * eski sessiz kanalları kullanmaya devam etmiyoruz.
     */
    private static final String REMINDER_CHANNEL_ID =
            "hilal_reminders_v9_sound";

    private static final String EZAN_CHANNEL_ID =
            "hilal_ezan_v9_sound";

    @Override
    public void onReceive(Context context, Intent source) {

        final PendingResult pendingResult = goAsync();

        NotificationManager manager =
                (NotificationManager) context.getSystemService(
                        Context.NOTIFICATION_SERVICE);

        if (manager == null) {
            pendingResult.finish();
            return;
        }

        String id = source.getStringExtra("id");
        String title = source.getStringExtra("title");
        String body = source.getStringExtra("body");
        String soundPath = source.getStringExtra("soundPath");

        String safeId = id == null ? "" : id;

        boolean isEzan = safeId.startsWith("ezan::");

        String safeTitle;
        String safeBody;

        if (isEzan) {
            safeTitle = title == null
                    ? "🕌 Hilâl • Namaz Vakti"
                    : title;

            safeBody = body == null
                    ? "Namaz vakti yaklaşıyor"
                    : body;
        } else {
            safeTitle = title == null
                    ? "Hilâl Hatırlatıcı"
                    : title;

            safeBody = body == null
                    ? "Hatırlatma zamanı"
                    : body;
        }

        /*
         * Bildirime tıklayınca uygulamayı aç.
         */
        Intent open = new Intent(context, MainActivity.class);

        open.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        open.putExtra("hilalReminderId", safeId);

        open.setData(Uri.parse(
                "hilal://reminder/" +
                Uri.encode(safeId)
        ));

        PendingIntent content = PendingIntent.getActivity(
                context,
                safeId.hashCode(),
                open,
                PendingIntent.FLAG_UPDATE_CURRENT |
                PendingIntent.FLAG_IMMUTABLE
        );

        /*
         * Bildirim kanalları.
         *
         * Ezanın mevcut çalışan ses mantığını bozmamak için
         * sistem kanalından ayrıca ses üretmiyoruz.
         * Gerçek ses MediaPlayer ile aşağıda çalıyor.
         *
         * Böylece çift ses de oluşmaz.
         */
        if (Build.VERSION.SDK_INT >= 26) {

            if (!isEzan) {

                NotificationChannel channel =
                        new NotificationChannel(
                                REMINDER_CHANNEL_ID,
                                "Hilâl Hatırlatıcıları",
                                NotificationManager.IMPORTANCE_HIGH
                        );

                channel.setDescription(
                        "Vird, dua ve ibadet hatırlatmaları"
                );

                channel.setSound(null, null);

                channel.enableVibration(true);

                channel.setVibrationPattern(
                        new long[]{0, 300, 120, 300}
                );

                channel.setLockscreenVisibility(
                        Notification.VISIBILITY_PUBLIC
                );

                manager.createNotificationChannel(channel);

            } else {

                /*
                 * Ezan bildiriminde sistem kanal sesi kapalı.
                 * Mevcut ezan sesi MediaPlayer ile çalacak.
                 */
                NotificationChannel channel =
                        new NotificationChannel(
                                EZAN_CHANNEL_ID,
                                "Hilâl Ezan Bildirimleri",
                                NotificationManager.IMPORTANCE_HIGH
                        );

                channel.setDescription(
                        "Namaz vakti bildirimleri"
                );

                channel.setSound(null, null);

                channel.enableVibration(true);

                channel.setVibrationPattern(
                        new long[]{0, 180, 100, 180}
                );

                channel.setLockscreenVisibility(
                        Notification.VISIBILITY_PUBLIC
                );

                manager.createNotificationChannel(channel);
            }
        }

        String channelId =
                isEzan
                        ? EZAN_CHANNEL_ID
                        : REMINDER_CHANNEL_ID;

        /*
         * Hilâl premium bildirim görünümü.
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

        Notification.Builder note;

        if (Build.VERSION.SDK_INT >= 26) {

            note = new Notification.Builder(
                    context,
                    channelId
            );

        } else {

            note = new Notification.Builder(context);

        }

        note
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(safeTitle)
                .setContentText(safeBody)
                .setCustomContentView(compact)
                .setCustomBigContentView(expanded)
                .setCustomHeadsUpContentView(compact)
                .setContentIntent(content)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setCategory(
                        isEzan
                                ? Notification.CATEGORY_ALARM
                                : Notification.CATEGORY_REMINDER
                )
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(false)
                .setVibrate(
                        new long[]{0, 300, 120, 300}
                );

        /*
         * Android sistem bildiriminin kendi sesini kapalı tutuyoruz.
         *
         * Asıl ses aşağıdaki MediaPlayer'dan geliyor.
         */
        note.setSound(null);

        boolean shownInsideApp =
                MainActivity.deliverForegroundReminder(
                        safeId,
                        safeTitle,
                        safeBody
                );

        try {

            /*
             * Uygulama açık ve aktif:
             * sadece Hilâl'in uygulama içi bildirimi.
             *
             * Uygulama arka planda / kapalı / ekran kilitli:
             * Android sistem bildirimi.
             */
            if (!shownInsideApp) {

                manager.notify(
                        safeId.hashCode(),
                        note.build()
                );
            }

        } catch (SecurityException ignored) {
            // Bildirim izni yoksa uygulama çökmesin.
        }

        /*
         * Bir sonraki tekrarı planla.
         */
        ReminderScheduler.afterFire(
                context,
                source
        );

        /*
         * ÖNEMLİ:
         *
         * Önceki kodda bildirim sesi seviyesi %50'nin altındaysa
         * ses tamamen kesiliyordu.
         *
         * BUNU KALDIRDIK.
         *
         * Telefon sessizdeyse Android'in sessiz davranışına saygı
         * gösteriyoruz; fakat normal durumda Hilâl sesi artık
         * %50 şartına takılmayacak.
         */
        playSelectedSound(
                context,
                soundPath,
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

            /*
             * Bildirim sesi olarak çalıştır.
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

            AtomicBoolean finished =
                    new AtomicBoolean(false);

            Handler handler =
                    new Handler(Looper.getMainLooper());

            MediaPlayer finalPlayer = player;

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

            /*
             * Öncelik:
             *
             * 1. Kullanıcının seçtiği özel ses
             * 2. Uygulamadaki ses URL'sinden indirilmiş dosya
             * 3. Telefonun varsayılan bildirim sesi
             */
            if (soundPath != null &&
                    !soundPath.trim().isEmpty() &&
                    new File(soundPath).isFile()) {

                player.setDataSource(soundPath);

            } else {

                Uri defaultSound =
                        RingtoneManager.getDefaultUri(
                                RingtoneManager.TYPE_NOTIFICATION
                        );

                if (defaultSound == null) {
                    pendingResult.finish();
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

            player.start();

            /*
             * Uzun ses dosyalarında BroadcastReceiver'ın sonsuza
             * kadar açık kalmasını engelle.
             */
            handler.postDelayed(
                    finish,
                    15000L
            );

        } catch (Exception error) {

            try {
                if (player != null) {
                    player.release();
                }
            } catch (Exception ignored) {
            }

            pendingResult.finish();
        }
    }
}             handler.removeCallbacks(finish);
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
