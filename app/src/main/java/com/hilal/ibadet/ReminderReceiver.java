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
    private static final String SOUND_CHANNEL_PREFIX = "hilal_reminders_sound_v8_";
    private static final String VIBRATE_CHANNEL_ID = "hilal_reminders_v8_vibrate";

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
            // Normal modda seçilen ses Android bildirim kanalının gerçek sesi olarak ayarlanır.
            // Kanal ID'si ses bazında benzersiz olduğu için daha önce sessize alınmış v7 kanalına
            // takılmaz.
            if (!lowOrSilent && soundEnabled) {
                // Ses tek bir yerden, aşağıdaki RingtoneManager yolundan çalınır.
                // Kanalı sessiz tutarak çift zil oluşmasını önlüyoruz.
                channel.setSound(null, null);
                channel.enableVibration(false);
            } else {
                channel.setSound(null, null);
                channel.enableVibration(false);
            }
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

        // Normal hatırlatıcıda fav1-fav4 ve telefon sesi Android bildirim kanalı üzerinden
        // çalar. Uygulama ön plandaysa sistem bildirimi gösterilmediği için aynı sesi doğrudan
        // oynat; özel dosya seçilmişse kanal özel URI taşıyamadığından doğrudan oynat.
        if (soundEnabled) {
            playSelectedSound(context, selectedSound, source.getStringExtra("soundPath"), pendingResult);
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
        // Normal hatırlatıcı sesi için MediaPlayer yerine Android'in kendi Ringtone
        // motorunu kullanıyoruz. Ezan tarafındaki gömülü fav1-fav4 kaynakları aynen
        // kullanılır. Böylece BroadcastReceiver arka plandayken de sistem bildirim
        // ses yoluyla aynı zil güvenilir şekilde çalınır.
        try {
            Uri soundUri = null;

            if ("fav1".equals(soundId) || "fav2".equals(soundId)
                    || "fav3".equals(soundId) || "fav4".equals(soundId)) {
                int rawSound = getFavoriteRawSound(context, soundId);
                if (rawSound != 0) {
                    soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + rawSound);
                }
            } else if ("phone".equals(soundId)) {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            } else if ("custom".equals(soundId) && soundPath != null && !soundPath.isEmpty()) {
                File f = new File(soundPath);
                if (f.isFile() && f.length() > 0) {
                    soundUri = Uri.fromFile(f);
                }
            }

            if (soundUri == null) {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            if (soundUri == null) {
                pendingResult.finish();
                return;
            }

            final android.media.Ringtone ringtone = RingtoneManager.getRingtone(context, soundUri);
            if (ringtone == null) {
                pendingResult.finish();
                return;
            }

            if (Build.VERSION.SDK_INT >= 21) {
                try {
                    ringtone.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build());
                } catch (Exception ignored) { }
            } else {
                try { ringtone.setStreamType(AudioManager.STREAM_NOTIFICATION); } catch (Exception ignored) { }
            }

            ringtone.play();

            // Zil sesinin tamamlanmasına izin ver. Uzun/bozuk bir dosya Receiver'ı
            // sonsuza kadar açık bırakmasın.
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { if (ringtone.isPlaying()) ringtone.stop(); } catch (Exception ignored) { }
                pendingResult.finish();
            }, 15000L);
        } catch (Exception ignored) {
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
