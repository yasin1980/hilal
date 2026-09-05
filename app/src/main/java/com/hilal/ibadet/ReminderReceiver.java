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

            channel.setLockscreenVisibility(
                    Notification.VISIBILITY_PUBLIC
            );

            manager.createNotificationChannel(
                    channel
            );
        }

        /*
         * Android'in kendi profesyonel bildirim görünümünü kullanıyoruz.
         *
         * Böylece beyaz sistem kartının üzerine ayrıca
         * yeşil RemoteViews binmeyecek.
         */
        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            builder =
                    new Notification.Builder(
                            context,
                            CHANNEL_ID
                    );

        } else {

            builder =
                    new Notification.Builder(
                            context
                    );

            builder.setSound(
                    RingtoneManager.getDefaultUri(
                            RingtoneManager.TYPE_NOTIFICATION
                    )
            );

            builder.setVibrate(
                    VIBRATION_PATTERN
            );
        }

        /*
         * Hilâl bildirim simgesi.
         */
        builder.setSmallIcon(
                R.drawable.ic_notification
        );

        builder.setContentTitle(
                title
        );

        builder.setContentText(
                body
        );

        builder.setContentIntent(
                contentIntent
        );

        builder.setAutoCancel(
                true
        );

        builder.setPriority(
                Notification.PRIORITY_HIGH
        );

        builder.setCategory(
                Notification.CATEGORY_REMINDER
        );

        builder.setVisibility(
                Notification.VISIBILITY_PUBLIC
        );

        /*
         * Titreşim.
         */
        builder.setVibrate(
                VIBRATION_PATTERN
        );

        /*
         * Uygulama açıkken mevcut Hilâl içi bildirim sistemi
         * çalışmaya devam eder.
         */
        boolean shownInsideApp =
                MainActivity.deliverForegroundReminder(
                        id,
                        title,
                        body
                );

        /*
         * Uygulama kapalıysa Android sistem bildirimi gösterilir.
         */
        if (!shownInsideApp) {

            try {

                manager.notify(
                        id == null
                                ? 1
                                : id.hashCode(),
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
    }
}
