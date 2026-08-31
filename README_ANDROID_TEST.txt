HİLÂL ANDROID TELEFON TEST PAKETİ

Bu proje son çalışan Hilâl index.html sürümünü Android WebView içine gömer.
Android tarafında:
- İnternet/Supabase bağlantısı açıktır.
- GPS izni istenir ve HTML navigator.geolocation kullanabilir.
- Android ROTATION_VECTOR sensörü doğrudan HTML'deki setHeading(...) fonksiyonuna aktarılır.
- Hilâl uygulama ikonu launcher icon olarak ayarlanmıştır.

APK üretme:
1. Android Studio ile bu klasörü açın.
2. Gradle Sync tamamlanınca telefonunuzu USB ile bağlayın veya Build > Build APK(s) seçin.
3. Oluşan debug APK: app/build/outputs/apk/debug/app-debug.apk
4. APK'yı WhatsApp/Drive/Quick Share ile ikinci telefona gönderip kurabilirsiniz.

İKİ TELEFONDA GERÇEK ZAMANLI TEST
- İki telefonda internet açık olsun.
- Aynı Supabase projesine bağlı bu sürümü kurun.
- Telefon 1: yönetici ile giriş yapıp bir hoca/kullanıcı yetkisini değiştirin veya yayın/duyuru oluşturun.
- Telefon 2: ilgili hesapla giriş yapın ve değişikliğin görünmesini kontrol edin.
- Eğer anlık görünmüyor ama uygulama yeniden açılınca görünüyorsa realtime subscription eksiktir; yalnızca veri tabanı kaydı çalışıyordur.

Not: Bu ortamda Android SDK/Gradle kurulu olmadığı için burada APK binary'si derlenemedi; proje APK üretmeye hazırdır.
