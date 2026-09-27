
(function(){
  'use strict';
  function cleanSettings(){
    const settings=document.getElementById('settingsPage'); if(!settings)return;
    const first=settings.querySelector('.settings-stack > article.card:first-child');
    if(first && /Görünüm/i.test(first.textContent||'')) first.remove();
    settings.querySelectorAll('.hilal-settings-tools-v1').forEach(x=>x.remove());
  }
  function init(){cleanSettings();setTimeout(cleanSettings,300);setTimeout(cleanSettings,1000)}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);else init();
  document.addEventListener('click',e=>{if(e.target.closest?.('[data-page="settingsPage"], [data-tpage="settingsPage"], [data-gpage="settingsPage"], #settingsPage'))setTimeout(cleanSettings,80)},true);
})();
</script>
<style id="hilal-v81-settings-final-hide">
#settingsPage .settings-stack > article.card:has(h3:first-child){ }
#settingsPage .hilal-settings-tools-v1{display:none!important}
</style>
<!-- =========================================================
 HİLÂL V8.2 — TEK NAVİGASYON / GÜVENLİ SÜRÜM
 Orijinal DOM yapısı ve sayfa açma fonksiyonları değiştirilmez.
 ========================================================= -->
<!-- =========================================================
 HİLÂL V8.2 — YALNIZ NAVİGASYON GÖRSEL/YERLEŞİM DÜZELTMESİ
 Duyurular referansı: hareketli menü solda, sabit geri hemen sağında.
 Diğer uygulama içeriğine/fonksiyonlarına dokunmaz.
 ========================================================= -->
<!-- =========================================================
 HİLÂL — ORTAK NAVİGASYON BİLEŞENİ
 Tek kaynak: 1 menü + 1 geri butonu. Sayfalar bu sınıfı kullanır.
 ========================