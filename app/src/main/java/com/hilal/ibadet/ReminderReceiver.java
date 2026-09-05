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
import android.os.PowerManager;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReminderReceiver extends BroadcastReceiver {

    private static final String REMINDER_CHANNEL_ID = "hilal_reminders_v9_sound";
    private static final String EZAN_CHANNEL_ID = "hilal_ezan_v9_sound";

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
        String title = source.getStringExtra("title");
        String body = source.getStringExtra("body");
        String soundPath = source.getStringExtra("soundPath");
        String sound = source.getStringExtra("sound");

        String safeId = id == null ? "" : id;
        boolean isEzan = safeId.startsWith("ezan::");

        String safeTitle = title == null
                ? (isEzan ? "🕌 Hilâl • Namaz Vakti" : "Hilâl Hatırlatıcı")
                : title;
        String safeBody = body == null
                ? (isEzan ? "Namaz vakti yaklaşıyor" : "Hatırlatma zamanı")
                : body;

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra("hilalReminderId", safeId);
        open.setData(Uri.parse("hilal://reminder/" + Uri.encode(safeId)));

        PendingIntent content = PendingIntent.getActivity(
                context,
                safeId.hashCode(),
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String channelId = isEzan ? EZAN_CHANNEL_ID : REMINDER_CHANNEL_ID;

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    isEzan ? "Hilâl Ezan Bildirimleri" : "Hilâl Hatırlatıcıları",
                    NotificationManager.IMPORTANCE_HIGH);

            channel.setDescription(isEzan
                    ? "Namaz vakti bildirimleri"
                    : "Vird, dua ve ibadet hatırlatmaları");

            // Ses dosyasını MediaPlayer ile kontrollü şekilde çalıyoruz.
            // Böylece aynı sesin sistem tarafından ikinci kez çalması önlenir.
            channel.setSound(null, null);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 300, 120, 300});
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(channel);
        }

        android.widget.RemoteViews compact = new android.widget.RemoteViews(
                context.getPackageName(), R.layout.notification_hilal);
        compact.setTextViewText(android.R.id.title, safeTitle);
        compact.setTextViewText(android.R.id.text1, safeBody);

        android.widget.RemoteViews expanded = new android.widget.RemoteViews(
                context.getPackageName(), R.layout.notification_hilal);
        expanded.setTextViewText(android.R.id.title, safeTitle);
        expanded.setTextViewText(android.R.id.text1, safeBody);

        Notification.Builder note = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, channelId)
                : new Notification.Builder(context);

        note.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(safeTitle)
                .setContentText(safeBody)
                .setCustomContentView(compact)
                .setCustomBigContentView(expanded)
                .setCustomHeadsUpContentView(compact)
                .setContentIntent(content)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setCategory(isEzan
                        ? Notification.CATEGORY_ALARM
                        : Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setVibrate(new long[]{0, 300, 120, 300})
                .setSound(null);

        boolean shownInsideApp = MainActivity.deliverForegroundReminder(
                safeId,
                safeTitle,
                safeBody);

        try {
            // Uygulama açık ve aktifse yalnızca Hilâl'in uygulama içi bildirimi.
            // Arka planda, kapalıyken veya kilit ekranındayken sistem bildirimi.
            if (!shownInsideApp) {
                manager.notify(safeId.hashCode(), note.build());
            }
        } catch (SecurityException ignored) {
            // Android 13+ bildirim izni verilmemişse uygulama çökmesin.
        }

        ReminderScheduler.afterFire(context, source);

        // Normal hatırlatıcılarda ve ezan bildirimlerinde seçilen ses dosyasını
        // doğrudan çalıyoruz. Bildirim sesi seviyesi %50 altında diye sesi kesmiyoruz.
        playSelectedSound(context, sound, soundPath, pendingResult);
    }

    private void playSelectedSound(
            Context context,
            String sound,
            String soundPath,
            PendingResult pendingResult) {

        MediaPlayer player = null;
        PowerManager.WakeLock wakeLock = null;

        try {
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (power != null) {
                wakeLock = power.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        context.getPackageName() + ":hilal_reminder_sound");
                wakeLock.acquire(20_000L);
            }

            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());

            AtomicBoolean finished = new AtomicBoolean(false);
            Handler handler = new Handler(Looper.getMainLooper());
            MediaPlayer finalPlayer = player;
            PowerManager.WakeLock finalWakeLock = wakeLock;

            Runnable finish = () -> {
                if (!finished.compareAndSet(false, true)) return;
                handler.removeCallbacksAndMessages(null);
                try {
                    if (finalPlayer.isPlaying()) finalPlayer.stop();
                } catch (Exception ignored) { }
                try {
                    finalPlayer.release();
                } catch (Exception ignored) { }
                try {
                    if (finalWakeLock != null && finalWakeLock.isHeld()) finalWakeLock.release();
                } catch (Exception ignored) { }
                pendingResult.finish();
            };

            boolean hasFile = soundPath != null
                    && !soundPath.trim().isEmpty()
                    && new File(soundPath).isFile();

            if (hasFile) {
                player.setDataSource(soundPath);
            } else if ("classic".equals(sound)) {
                Uri classic = Uri.parse("android.resource://" + context.getPackageName()
                        + "/" + R.raw.hilal_classic_notification);
                player.setDataSource(context, classic);
            } else {
                Uri defaultSound = RingtoneManager.getDefaultUri(
                        RingtoneManager.TYPE_NOTIFICATION);
                if (defaultSound == null) {
                    finish.run();
                    return;
                }
                player.setDataSource(context, defaultSound);
            }

            player.setOnCompletionListener(mp -> finish.run());
            player.setOnErrorListener((mp, what, extra) -> {
                finish.run();
                return true;
            });

            player.prepare();
            player.setVolume(1.0f, 1.0f);
            player.start();

            // Ses uzun sürerse bile receiver sonsuza kadar açık kalmasın.
            handler.postDelayed(finish, 20_000L);

        } catch (Exception ignored) {
            try {
                if (player != null) player.release();
            } catch (Exception ignoredAgain) { }
            try {
                if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            } catch (Exception ignoredAgain) { }
            pendingResult.finish();
        }
    }

                    }
