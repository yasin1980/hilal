
/*
 HILÂL KALICI MODÜLER ÇALIŞMA SİSTEMİ
 ------------------------------------
 Bu kayıt katmanı çalışan mevcut motorları değiştirmez.
 Bundan sonraki geliştirmelerde yalnız adı verilen modül hedeflenir.
 Ortak UI: HilalCore / HilalUI
*/
(function () {
  "use strict";
  if (window.HilalModules) return;

  const modules = Object.create(null);

  window.HilalModules = Object.freeze({
    names: Object.freeze([
      "HilalTakvimEzan",
      "HilalBugununIbadetleri",
      "HilalDuaIstekleri",
      "HilalGenelDuyurular",
      "HilalGenelYayinlar",
      "HilalYayinlarim",
      "HilalFavoriler",
      "HilalDuyurularim",
      "HilalZikirmatik",
      "HilalKazaNamazlari",
      "HilalOneriSikayet",
      "HilalAyarlar"
    ]),
    register(name, api) {
      if (!name || modules[name]) return modules[name] || null;
      modules[name] = Object.freeze(api || {});
      return modules[name];
    },
    get(name) { return modules[name] || null; },
    has(name) { return !!modules[name]; }
  });

  window.HilalCore = window.HilalCore || Object.create(null);
  window.HilalUI = window.HilalUI || Object.create(null);

  /* Kilitli çalışan motorlar: değişiklik yalnız açıkça kendi modülü istendiğinde. */
  window.HilalModulePolicy = Object.freeze({
    isolated: true,
    locked: Object.freeze([
      "HilalDuaIstekleri",
      "HilalZikirmatik",
      "HilalKazaNamazlari"
    ]),
    rule: "ONLY_NAMED_MODULE_MAY_CHANGE"
  });
})();
</script>
<!-- HILAL: isolated Qibla camera overlay -->
<style id="hilal-qibla-camera-clean-v1">
#calendarPage .hilal-qibla-v1428{position:relative!important;overflow:hidden!important;isolation:isolate!important;min-height:clamp(430px,72vh,680px)!important;background:#063d31!important}
#calendarPage .hilal-qibla-v1428 .qcam-video{position:absolute!important;inset:0!important;width:100%!important;height:100%!important;object-fit:cover!important;z-index:0!important;background:#063d31!important}
#calendarPage .hilal-qibla-v1428 .qcam-shade{position:absolute!important;inset:0!important;z-index:1!important;pointer-events:none!important;background:linear-gradient(180deg,rgba(1,25,20,.38),rgba(1,25,20,.08) 35%,rgba(1,25,20,.34))!important}
#calendarPage .hilal-qibla-v1428>.q1428-head,#calendarPage .hilal-qibla-v1428>.q1428-compass,#calendarPage .hilal-qibla-v1428>.q1431-alignment{position:relative!important;z-index:2!important}
#calendarPage .hilal-qibla-v1428>.q1428-compass{wi