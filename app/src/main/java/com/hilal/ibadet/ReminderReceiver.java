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
import android.os.Vibrator;
import android.os.VibrationEffect;
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
        // Bildirim içindeki eski kayıt tarihlerini gösterme; tarih her bildirim
        // tetiklendiğinde yeniden hesaplanıp en başta güncel olarak gösterilsin.
        safeBody = safeBody.replaceAll("\\b\\d{1,2}[./-]\\d{1,2}[./-]\\d{4}\\b", " ");
        safeBody = safeBody.replaceAll("\\b\\d{1,2}\\s+(?:Muharrem|Safer|Rebiülevvel|Rebiülahir|Cemaziyelevvel|Cemaziyelahir|Recep|Şaban|Ramazan|Şevval|Zilkade|Zilhicce)\\s+\\d{3,4}\\b", " ");
        safeBody = safeBody.replaceAll("\\s{2,}", " ").trim();
        safeBody = currentDates + " • " + safeBody;
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

        // Normal hatırlatıcılar için titreşim doğrudan Vibrator ile verilir.
        // Böylece bildirim kanalı daha önce sessiz oluşturulmuş olsa bile
        // hatırlatıcı zamanı geldiğinde titreşim kesin olarak çalışır.
        vibrateReminder(context);

        // Normal hatırlatıcı zil sesi açık olarak zorlanır. Ezan zincirine dokunulmaz.
        boolean soundEnabled = source.getBooleanExtra("soundEnabled", true);
        if (soundEnabled) {
            playSelectedSound(context, source.getStringExtra("sound"), source.getStringExtra("soundPath"), pendingResult);
        } else {
            pendingResult.finish();
        }
    }

    private void vibrateReminder(Context context) {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            long[] pattern = new long[]{0L, 260L, 120L, 260L, 120L, 360L};
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        } catch (Exception ignored) { }
    }

    private void playSelectedSound(Context context, String soundId, String soundPath, PendingResult pendingResult) {
        MediaPlayer player = null;
        try {
            // Seçilen hatırlatıcı sesini doğrudan uygulamanın kaydettiği dosyadan çal.
            // Bu yol, uygulama açık/kapalı olsa da aynı şekilde çalışır.
            String playablePath = null;
            if (soundPath != null && !soundPath.isEmpty()) {
                File f = new File(soundPath);
                if (f.isFile() && f.length() > 0) playablePath = f.getAbsolutePath();
            }
            if (playablePath == null) {
                String embedded = extractEmbeddedFavoriteSound(context, soundId);
                if (embedded != null) {
                    File f = new File(embedded);
                    if (f.isFile() && f.length() > 0) playablePath = f.getAbsolutePath();
                }
            }

            // Favori bildirim seslerini APK içindeki gerçek MP3 kaynaklarından çal.
            // Böylece arka planda çalışan AlarmManager alıcısı HTML/Base64 ayrıştırmasına
            // veya geçici dosya erişimine bağlı kalmaz.
            int rawSound = getFavoriteRawSound(soundId);
            boolean preparedFromResource = rawSound != 0;
            if (preparedFromResource) {
                // Favori seslerde zamanlama sırasında oluşturulan kopyayı değil,
                // APK içindeki doğrulanmış MP3 kaynağını kullan.
                player = MediaPlayer.create(context, rawSound);
            } else {
                player = new MediaPlayer();
            }
            if (player == null) {
                pendingResult.finish();
                return;
            }
            if (Build.VERSION.SDK_INT >= 21) {
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
            }
            try { player.setAudioStreamType(AudioManager.STREAM_NOTIFICATION); } catch (Exception ignored) { }
            player.setVolume(1.0f, 1.0f);
            if (Build.VERSION.SDK_INT >= 23) {
                try { player.setWakeMode(context, android.os.PowerManager.PARTIAL_WAKE_LOCK); } catch (Exception ignored) { }
            }

            if (!preparedFromResource && playablePath != null) {
                player.setDataSource(playablePath);
            } else if (!preparedFromResource) {
                // Seçilen dosya bulunamazsa sessiz kalmak yerine telefonun varsayılan
                // bildirim sesini çal. Ezan ses zincirine dokunulmaz.
                Uri fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                if (fallback == null) {
                    pendingResult.finish();
                    return;
                }
                player.setDataSource(context, fallback);
            }

            final MediaPlayer mp = player;
            final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            try {
                if (audioManager != null) {
                    audioManager.requestAudioFocus(null, AudioManager.STREAM_NOTIFICATION, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
                }
            } catch (Exception ignored) { }
            final Handler handler = new Handler(Looper.getMainLooper());
            final AtomicBoolean finished = new AtomicBoolean(false);
            final Runnable finish = () -> {
                if (!finished.compareAndSet(false, true)) return;
                try { if (mp.isPlaying()) mp.stop(); } catch (Exception ignored) { }
                try { mp.reset(); } catch (Exception ignored) { }
                try { mp.release(); } catch (Exception ignored) { }
                try { if (audioManager != null) audioManager.abandonAudioFocus(null); } catch (Exception ignored) { }
                pendingResult.finish();
            };

            mp.setOnCompletionListener(x -> finish.run());
            mp.setOnErrorListener((x, what, extra) -> {
                finish.run();
                return true;
            });
            if (!preparedFromResource) mp.prepare();
            mp.start();
            // Receiver'ın yaşam süresini ses bitene kadar tut; çok uzun dosyalarda
            // AlarmManager alıcısının sonsuza kadar açık kalmasını önle.
            handler.postDelayed(finish, 15000L);
        } catch (Exception ignored) {
            try { if (player != null) player.release(); } catch (Exception ignored2) { }
            pendingResult.finish();
        }
    }
    private int getFavoriteRawSound(String soundId) {
        // Kaynak ID'sini çalışma zamanında çöz: farklı Android/Gradle kaynak
        // üretim yapılandırmalarında R.raw sembolüne derleme bağımlılığı olmaz.
        if (soundId == null || soundId.isEmpty()) return 0;
        try {
            return getResources(context).getIdentifier(
                    "hilal_reminder_" + soundId, "raw", context.getPackageName());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private android.content.res.Resources getResources(Context context) {
        return context.getResources();
    }

    private String getCurrentDateLine() {
        try {
            Calendar now = Calendar.getInstance();
            String miladi = new SimpleDateFormat("dd.MM.yyyy", java.util.Locale.US).format(now.getTime());
            android.icu.util.IslamicCalendar hijri = new android.icu.util.IslamicCalendar(android.icu.util.TimeZone.getDefault(), java.util.Locale.forLanguageTag("tr-TR"));
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
