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
    private static final String ACTION_STOP_AND_OPEN = "com.hilal.ibadet.STOP_AND_OPEN_REMINDER";
    private static volatile MediaPlayer activePlayer;
    private static final Object PLAYER_LOCK = new Object();

    private static void stopActiveSound() {
        synchronized (PLAYER_LOCK) {
            MediaPlayer mp = activePlayer;
            activePlayer = null;
            if (mp != null) {
                try { if (mp.isPlaying()) mp.stop(); } catch (Exception ignored) {}
                try { mp.release(); } catch (Exception ignored) {}
            }
        }
    }

    private static final String SOUND_CHANNEL_PREFIX = "hilal_reminders_sound_v10_";
    private static final String VIBRATE_CHANNEL_ID = "hilal_reminders_v10_vibrate";

    @Override public void onReceive(Context context, Intent source) {
        if (ACTION_STOP_AND_OPEN.equals(source.getAction())) {
            stopActiveSound();
            Intent openTapped = new Intent(context, MainActivity.class);
            openTapped.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            String tappedId = source.getStringExtra("id");
            if (tappedId != null) {
                openTapped.putExtra("hilalReminderId", tappedId);
                openTapped.setData(Uri.parse("hilal://reminder/" + Uri.encode(tappedId)));
            }
            try { context.startActivity(openTapped); } catch (Exception ignored) {}
            return;
        }

        final PendingResult pendingResult = goAsync();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) { pendingResult.finish(); return; }
        Intent open = new Intent(context, MainActivity.class);
        String id = source.getStringExtra("id");
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra("hilalReminderId", id);
        open.setData(Uri.parse("hilal://reminder/" + Uri.encode(id == null ? "" : id)));
        Intent tapIntent = new Intent(context, ReminderReceiver.class);
        tapIntent.setAction(ACTION_STOP_AND_OPEN);
        tapIntent.putExtra("id", id);
        PendingIntent content = PendingIntent.getBroadcast(context, id == null ? 0 : id.hashCode(), tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        boolean lowOrSilent = audio == null || audio.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
        String selectedSound = source.getStringExtra("sound");
        if (selectedSound == null || selectedSound.isEmpty()) selectedSound = "fav1";
        boolean soundEnabled = source.getBooleanExtra("soundEnabled", true);
        String channelId = lowOrSilent ? VIBRATE_CHANNEL_ID : getSoundChannelId(selectedSound);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(channelId,
                    lowOrSilent ? "Hilâl Hatırlatıcıları (Titreşim)" : getSoundChannelName(selectedSound),
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Vird, dua, ibadet ve hatırlatıcı bildirimleri");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            // ÖNEMLİ: Android 8+ cihazlarda bildirim sesinin asıl kaynağı NotificationChannel
            // ayarıdır. Burada kanalı sessize alıp ayrıca Ringtone çaldırmak yerine, ezan tarafında
            // kullanılan aynı gömülü fav1-fav4 MP3'lerini doğrudan kanala bağlıyoruz. Her sesin
            // ayrı ve yeni bir kanal ID'si var; eski sessiz kanallar bu yüzden devre dışı kalır.
            // Android 8+ kanal sesine güvenmiyoruz. Kanalı sessiz tutup seçilen
            // sesi aşağıda doğrudan uygulamanın ses kaynağından çalıyoruz. Böylece
            // Xiaomi/Android'in daha önce oluşturulmuş kanal ayarları zil sesini
            // değiştiremez veya susturamaz.
            channel.setSound(null, null);
            channel.enableVibration(false);
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
                .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PUBLIC);
        // Android 7 ve altı için kanal olmadığı için sesi doğrudan bildirime ver.
        if (Build.VERSION.SDK_INT < 26 && soundEnabled) {
            try { note.setSound(getNotificationSoundUri(context, selectedSound)); } catch (Exception ignored) { }
        }
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

        // Seçilen gömülü zil (fav1-fav4) ve özel dosya için sesi her Android sürümünde
        // doğrudan uygulama dosyasından çal. Android 8+ NotificationChannel sesine
        // güvenmiyoruz; kanal yukarıda bilerek sessiz oluşturuldu.
        // Telefonun Bildirim Sesi ise sistem bildirimi/kanal üzerinden çalışmaya devam eder.
        if (soundEnabled) {
            boolean directSound = "fav1".equals(selectedSound) || "fav2".equals(selectedSound)
                    || "fav3".equals(selectedSound) || "fav4".equals(selectedSound)
                    || "custom".equals(selectedSound);
            if (directSound || shownInsideApp || Build.VERSION.SDK_INT < 26) {
                playSelectedSound(context, selectedSound, source.getStringExtra("soundPath"), pendingResult);
            } else {
                pendingResult.finish();
            }
        } else {
            pendingResult.finish();
        }
    }

    private String getSoundChannelId(String soundId) {
        if ("phone".equals(soundId)) return SOUND_CHANNEL_PREFIX + "phone";
        if ("fav1".equals(soundId) || "fav2".equals(soundId) || "fav3".equals(soundId) || "fav4".equals(soundId)) {
            return SOUND_CHANNEL_PREFIX + soundId;
        }
        return SOUND_CHANNEL_PREFIX + "default";
    }

    private String getSoundChannelName(String soundId) {
        if ("phone".equals(soundId)) return "Hilâl • Telefonun Bildirim Sesi";
        if ("fav1".equals(soundId)) return "Hilâl • Bildirim Zil 1";
        if ("fav2".equals(soundId)) return "Hilâl • Bildirim Zil 2";
        if ("fav3".equals(soundId)) return "Hilâl • Bildirim Zil 3";
        if ("fav4".equals(soundId)) return "Hilâl • Bildirim Zil 4";
        return "Hilâl • Hatırlatıcı";
    }

    private Uri getNotificationSoundUri(Context context, String soundId) {
        try {
            if ("phone".equals(soundId)) {
                return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            int raw = getFavoriteRawSound(context, soundId);
            if (raw != 0) {
                return Uri.parse("android.resource://" + context.getPackageName() + "/" + raw);
            }
        } catch (Exception ignored) { }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
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
            player = new MediaPlayer();
            final MediaPlayer mp = player;

            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            if (Build.VERSION.SDK_INT >= 21) mp.setAudioAttributes(attrs);
            else mp.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);

            boolean isFavorite = "fav1".equals(soundId) || "fav2".equals(soundId)
                    || "fav3".equals(soundId) || "fav4".equals(soundId);

            // Öncelik: MainActivity'nin zaten hazırladığı gerçek dosya.
            File file = null;
            if (soundPath != null && !soundPath.isEmpty()) {
                File candidate = new File(soundPath);
                if (candidate.isFile() && candidate.length() > 0) file = candidate;
            }

            // fav1-fav4 için raw kaynak doğrudan açılır. Bu yol, WebView/asset
            // Base64'ünden bağımsızdır ve APK'nın içine gömülü sesi kullanır.
            if (file != null) {
                mp.setDataSource(file.getAbsolutePath());
            } else if (isFavorite) {
                int rawSound = getFavoriteRawSound(context, soundId);
                if (rawSound != 0) {
                    Uri uri = Uri.parse("android.resource://" + context.getPackageName() + "/" + rawSound);
                    mp.setDataSource(context, uri);
                } else {
                    String extracted = extractEmbeddedFavoriteSound(context, soundId);
                    if (extracted == null) {
                        mp.release();
                        pendingResult.finish();
                        return;
                    }
                    mp.setDataSource(extracted);
                }
            } else if ("phone".equals(soundId)) {
                Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                if (uri == null) { mp.release(); pendingResult.finish(); return; }
                mp.setDataSource(context, uri);
            } else {
                mp.release();
                pendingResult.finish();
                return;
            }

            mp.setOnCompletionListener(done -> {
                synchronized (PLAYER_LOCK) { if (activePlayer == done) activePlayer = null; }
                try { done.release(); } catch (Exception ignored) { }
                pendingResult.finish();
            });
            mp.setOnErrorListener((failed, what, extra) -> {
                synchronized (PLAYER_LOCK) { if (activePlayer == failed) activePlayer = null; }
                try { failed.release(); } catch (Exception ignored) { }
                pendingResult.finish();
                return true;
            });

            mp.prepare();
            synchronized (PLAYER_LOCK) {
                stopActiveSound();
                activePlayer = mp;
            }
            mp.start();

            // Çok uzun/bozuk bir dosyada BroadcastReceiver sonsuza kadar açık kalmasın.
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    if (mp.isPlaying()) {
                        mp.stop();
                    }
                } catch (Exception ignored) { }
                synchronized (PLAYER_LOCK) { if (activePlayer == mp) activePlayer = null; }
                try { mp.release(); } catch (Exception ignored) { }
                pendingResult.finish();
            }, 20000L);
        } catch (Exception error) {
            if (player != null) {
                try { player.release(); } catch (Exception ignored) { }
            }
            pendingResult.finish();
        }
    }

    private int getFavoriteRawSound(Context context, String soundId) {
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
