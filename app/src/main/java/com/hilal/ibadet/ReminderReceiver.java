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
    private static final String CHANNEL_ID = "hilal_reminders_v10";

    @Override public void onReceive(Context context, Intent source) {
        final PendingResult pendingResult = goAsync();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) { pendingResult.finish(); return; }

        String id = source.getStringExtra("id");
        String title = source.getStringExtra("title");
        String body = source.getStringExtra("body");
        String safeId = id == null ? "hilal-reminder" : id;
        boolean ezan = safeId.startsWith("ezan::");
        String safeTitle = title == null ? (ezan ? "🕌 Hilâl • Namaz Vakti" : "Hilâl Hatırlatıcı") : title;
        String safeBody = body == null ? (ezan ? "Namaz vakti yaklaşıyor" : "Hatırlatma zamanı") : body;

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra("hilalReminderId", safeId);
        open.setData(Uri.parse("hilal://reminder/" + Uri.encode(safeId)));
        PendingIntent content = PendingIntent.getActivity(context, safeId.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    ezan ? "Hilâl Ezan Bildirimleri" : "Hilâl Hatırlatıcıları",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(ezan ? "Namaz vakti bildirimleri" : "Vird, dua ve ibadet hatırlatmaları");
            channel.setSound(null, null);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 300, 120, 300});
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(channel);
        }

        android.widget.RemoteViews view = new android.widget.RemoteViews(context.getPackageName(), R.layout.notification_hilal);
        view.setTextViewText(android.R.id.title, safeTitle);
        view.setTextViewText(android.R.id.text1, safeBody);

        Notification.Builder note = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        note.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(safeTitle).setContentText(safeBody)
                .setCustomContentView(view).setCustomBigContentView(view).setCustomHeadsUpContentView(view)
                .setContentIntent(content).setAutoCancel(true).setPriority(Notification.PRIORITY_HIGH)
                .setCategory(ezan ? Notification.CATEGORY_ALARM : Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC).setVibrate(new long[]{0,300,120,300})
                .setSound(null);

        boolean shownInsideApp = MainActivity.deliverForegroundReminder(safeId, safeTitle, safeBody);
        try { if (!shownInsideApp) manager.notify(safeId.hashCode(), note.build()); } catch (SecurityException ignored) { }

        ReminderScheduler.afterFire(context, source);
        playSelectedSound(context, source.getStringExtra("soundPath"), pendingResult);
    }

    private void playSelectedSound(Context context, String soundPath, PendingResult pendingResult) {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio != null && audio.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            pendingResult.finish();
            return;
        }
        MediaPlayer player = null;
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            AtomicBoolean finished = new AtomicBoolean(false);
            Handler handler = new Handler(Looper.getMainLooper());
            MediaPlayer p = player;
            Runnable finish = () -> {
                if (!finished.compareAndSet(false, true)) return;
                try { if (p.isPlaying()) p.stop(); } catch (Exception ignored) { }
                try { p.release(); } catch (Exception ignored) { }
                pendingResult.finish();
            };
            if (soundPath != null && !soundPath.trim().isEmpty() && new File(soundPath).isFile()) {
                p.setDataSource(soundPath);
            } else {
                Uri fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                if (fallback == null) { pendingResult.finish(); return; }
                p.setDataSource(context, fallback);
            }
            p.setOnCompletionListener(mp -> { handler.removeCallbacks(finish); finish.run(); });
            p.setOnErrorListener((mp, what, extra) -> { handler.removeCallbacks(finish); finish.run(); return true; });
            p.prepare();
            p.setVolume(1.0f, 1.0f);
            p.start();
            handler.postDelayed(finish, 15000L);
        } catch (Exception ignored) {
            try { if (player != null) player.release(); } catch (Exception ignoredAgain) { }
            pendingResult.finish();
        }
    }
}
