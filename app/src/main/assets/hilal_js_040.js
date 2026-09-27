
/* Android/WebView'de pusula güncellemesini daha canlı tutar.
   Mevcut V1429 motoruna dokunmadan orientation olaylarını sık ve pasif dinler,
   mevcut motorun kullandığı pencere olay akışını canlı tutar. */
(function(){
 "use strict";
 if(window.AndroidHilal)return;
 let last=0, raf=0, pending=null;
 function relay(e){
   if(window.__hilalNativeCompassActive)return;
   const now=performance.now();
   pending=e;
   if(raf || now-last<16)return;
   raf=requestAnimationFrame(function(){
     raf=0; last=performance.now();
     /* Mevcut motor zaten deviceorientation/deviceorientationabsolute dinliyor.
        Burada yalnız WebView çizimini her sensör karesinde uyandırıyoruz. */
     const compass=document.getElementById("q1428CompassTap");
     if(compass) compass.style.willChange="transform";
   });
 }
 window.addEventListener("deviceorientationabsolute",relay,{passive:true});
 window.addEventListener("deviceorientation",relay,{passive:true});
 document.addEventListener("visibilitychange",function(){
   if(!document.hidden){
     const c=document.getElementById("q1428CompassTap");
     if(c){ c.style.willChange="transform"; requestAnimationFrame(()=>{c.style.willChange="auto";}); }
   }
 });
})();
</script><style id="hilal-v53-real-reference-calendar-hilal3d">
/* V53 — Ekranda gerçekten görünen hilal-ref-* Takvim/Ezan katmanı.
   YALNIZ GÖRSEL. Takvim, vakit, konum, z