package com.hilal.ibadet;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String SOUND_CHANNEL_ID = "hilal_reminders_v7_sound";
    private static final String VIBRATE_CHANNEL_ID = "hilal_reminders_v7_vibrate";

    @Override public void onReceive(Context context, Intent source) {
        final PendingResult pendingResult = goAsync();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) { pendingResult.finish(); return; }
        Intent open = new Intent(context, MainActivity.class);
        String id = source.getStringExtra("id");
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra("hilalReminderId", id);
        open.setData(Uri.parse("hilal://reminder/" + Uri.encode(id == null ? "" : id)));
        PendingIntent content = PendingIntent.getActivity(context, id == null ? 0 : id.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        boolean lowOrSilent = audio == null || audio.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;

        String channelId = lowOrSilent ? VIBRATE_CHANNEL_ID : SOUND_CHANNEL_ID;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(channelId,
                    lowOrSilent ? "Hilâl Hatırlatıcıları (Titreşim)" : "Hilâl Hatırlatıcıları",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setSound(null, null);
            channel.enableVibration(lowOrSilent);
            channel.setDescription("Vird, dua, ibadet ve ezan hatırlatmaları");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            if (lowOrSilent) channel.setVibrationPattern(new long[]{0, 260, 120, 260, 120, 360});
            manager.createNotificationChannel(channel);
        }

        String title = source.getStringExtra("title");
        String body = source.getStringExtra("body");
        String safeTitle = title == null ? "Hilâl Hatırlatıcı" : title;
        String safeBody = body == null ? "Hatırlatma zamanı" : body;
        String currentDates = getCurrentDateLine();
        if (!safeBody.contains(currentDates)) safeBody = safeBody + " • " + currentDates;
        android.widget.RemoteViews compact = new android.widget.RemoteViews(context.getPackageName(), R.layout.notification_hilal);
        compact.setTextViewText(android.R.id.title, safeTitle);
        compact.setTextViewText(android.R.id.text1, safeBody);
        android.widget.RemoteViews expanded = new android.widget.RemoteViews(context.getPackageName(), R.layout.notification_hilal);
        expanded.setTextViewText(android.R.id.title, safeTitle);
        expanded.setTextViewText(android.R.id.text1, safeBody);
        Notification.Builder note = new Notification.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(safeTitle)
                .setContentText(safeBody)
                .setCustomContentView(compact)
                .setCustomBigContentView(expanded)
                .setCustomHeadsUpContentView(compact)
                .setContentIntent(content).setAutoCancel(true).setPriority(Notification.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PUBLIC)
                .setSound(null);
        if (lowOrSilent) note.setVibrate(new long[]{0, 260, 120, 260, 120, 360});
        boolean shownInsideApp = MainActivity.deliverForegroundReminder(id, safeTitle, safeBody);
        try {
            // Uygulama ekranda açık ve aktifse yalnızca Hilâl'in kendi uygulama içi
            // bildirimi gösterilir; Android sistem bildirimi oluşturulmaz.
            if (!shownInsideApp) manager.notify(id == null ? 1 : id.hashCode(), note.build());
        } catch (SecurityException denied) {
            // Android 13+ bildirim izni reddedilmişse alıcı çökmeden güvenle devam eder.
        }
        ReminderScheduler.afterFire(context, source);
        if (lowOrSilent) {
            // Kullanıcının isteği: bildirim sesi yarının altındaysa veya sessizdeyse
            // sesi zorlamadan yalnızca belirgin titreşim kullan.
            pendingResult.finish();
        } else {
            playSelectedSound(context, source.getStringExtra("sound"), source.getStringExtra("soundPath"), pendingResult);
        }
    }

    private void playSelectedSound(Context context, String soundId, String soundPath, PendingResult pendingResult) {
        MediaPlayer player = null;
        AudioManager audioManager = null;
        try {
            audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            final AudioManager am = audioManager;
            final AudioManager.OnAudioFocusChangeListener focusListener = change -> { };
            if (am != null && Build.VERSION.SDK_INT >= 26) {
                try {
                    am.requestAudioFocus(new android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build())
                            .setOnAudioFocusChangeListener(focusListener)
                            .build());
                } catch (Exception ignored) { }
            }
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setVolume(1.0f, 1.0f);
            Uri sourceUri = null;
            if (soundPath != null && new File(soundPath).isFile() && new File(soundPath).length() > 0) {
                sourceUri = Uri.fromFile(new File(soundPath));
            } else {
                String embeddedPath = extractEmbeddedFavoriteSound(context, soundId);
                if (embeddedPath != null && new File(embeddedPath).isFile() && new File(embeddedPath).length() > 0) {
                    sourceUri = Uri.fromFile(new File(embeddedPath));
                }
            }
            if (sourceUri != null) {
                player.setDataSource(sourceUri.toString());
            } else {
                String embedded = extractEmbeddedFavoriteSound(context, soundId);
                if (embedded != null && new File(embedded).isFile() && new File(embedded).length() > 0) {
                    player.setDataSource(embedded);
                } else {
                    Uri fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                    player.setDataSource(context, fallback);
                }
            }

            final MediaPlayer mp = player;
            final AtomicBoolean finished = new AtomicBoolean(false);
            final Handler handler = new Handler(Looper.getMainLooper());
            final Runnable finish = () -> {
                if (!finished.compareAndSet(false, true)) return;
                try { if (mp.isPlaying()) mp.stop(); } catch (Exception ignored) { }
                try { mp.release(); } catch (Exception ignored) { }
                if (am != null) { try { am.abandonAudioFocus(focusListener); } catch (Exception ignored) { } }
                pendingResult.finish();
            };
            mp.setOnCompletionListener(x -> finish.run());
            mp.setOnErrorListener((x, what, extra) -> { finish.run(); return true; });
            mp.prepare();
            mp.start();
            handler.postDelayed(finish, 12000L);
        } catch (Exception ignored) {
            try { if (player != null) player.release(); } catch (Exception ignored2) { }
            pendingResult.finish();
        }
    }
    private String getCurrentDateLine() {
        try {
            Calendar now = Calendar.getInstance();
            String miladi = new SimpleDateFormat("dd.MM.yyyy", java.util.Locale.US).format(now.getTime());
            android.icu.util.IslamicCalendar hijri = new android.icu.util.IslamicCalendar(now.getTimeZone(), java.util.Locale.forLanguageTag("tr-TR"));
            hijri.setTimeInMillis(now.getTimeInMillis());
            String[] months = {"Muharrem", "Safer", "Rebiülevvel", "Rebiülahir", "Cemaziyelevvel", "Cemaziyelahir", "Recep", "Şaban", "Ramazan", "Şevval", "Zilkade", "Zilhicce"};
            int day = hijri.get(android.icu.util.Calendar.DAY_OF_MONTH);
            int month = hijri.get(android.icu.util.Calendar.MONTH);
            int year = hijri.get(android.icu.util.Calendar.YEAR);
            String monthName = (month >= 0 && month < months.length) ? months[month] : "";
            return miladi + " • " + day + " " + monthName + " " + year;
        } catch (Exception e) {
            return new SimpleDateFormat("dd.MM.yyyy", java.util.Locale.US).format(new java.util.Date());
        }
    }

    private String extractEmbeddedFavoriteSound(Context context, String soundId) {
        if (soundId == null || !(soundId.equals("fav1") || soundId.equals("fav2") || soundId.equals("fav3") || soundId.equals("fav4"))) return null;
        try {
            java.io.InputStream in = context.getAssets().open("index.html");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            String html = out.toString("UTF-8");
            String marker = "\"id\": \"" + soundId + "\", \"name\": ";
            int pos = html.indexOf(marker);
            if (pos < 0) return null;
            int srcPos = html.indexOf("\"src\": \"data:audio/", pos);
            if (srcPos < 0) return null;
            int valueStart = html.indexOf("data:audio/", srcPos);
            int valueEnd = html.indexOf('\"', valueStart);
            if (valueStart < 0 || valueEnd <= valueStart) return null;
            String data = html.substring(valueStart, valueEnd);
            int comma = data.indexOf(',');
            if (comma < 0) return null;
            byte[] audio = android.util.Base64.decode(data.substring(comma + 1), android.util.Base64.DEFAULT);
            File file = new File(context.getFilesDir(), "favorite_" + soundId + ".mp3");
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) { fos.write(audio); }
            return file.getAbsolutePath();
        } catch (Exception ignored) { return null; }
    }

}
