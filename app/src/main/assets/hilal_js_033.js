
(function(){
'use strict';

/* Yalnızca görsel durumları normalize eder; sayfa açma/kapatma işlevlerine dokunmaz. */
function stableVisualState(){
  /* Açılış ekranı görünürken rol kartları kesin görünür */
  const start=document.getElementById('startScreen');
  if(start && !start.classList.contains('hidden')){
    start.querySelectorAll(
      '.login-teacher-compact,.login-admin,.login-panels,.login-panel,.guest-entry-button'
    ).forEach(el=>{
      el.style.removeProperty('visibility');
      el.style.removeProperty('opacity');
    });
  }

  /* Dua alt sayfası gerçekten açıksa ortak geri hidden kalmasın */
  const feed=document.getElementById('hilalDuaFeedV55');
  const mine=document.getElementById('hilalDuaMyPageV53');
  const duaChild=!!(
    (feed && feed.classList.contains('hd55-open')) ||
    (mine && mine.classList.contains('hdv53-open'))
  );

  if(duaChild){
    const back=document.getElementById('hilalNavigationBack');
    if(back){
      back.hidden=false;
      back.removeAttribute('hidden');
    }
  }
}

/* class değişikliklerini tek karede normalize et; görünürlük döngüsü oluşturmaz */
let raf=0;
function queueStable(){
  if(raf) return;
  raf=requestAnimationFrame(()=>{
    raf=0;
    stableVisualState();
  });
}

/* V10: global DOM observer removed. It caused repeated full-document
   callbacks/repaints on older Android WebView. Dua state is synchronized by
   its own scoped controller plus explicit click/pageshow hooks below. */

document.addEventListener('DOMContentLoaded',stableVisualState,{once:true});
document.addEventListener('click',queueStable,true);
window.addEventListener('pageshow',stableVisualState);
setTimeout(stableVisualState,0);
})();
</script>
<style id="hilal-v89-feed-back-v90e-css">
/* V90E — SADECE Genel Dua alt sayfası için bağımsız geri butonu */
#hilalGenelDuaBackV90E{
  position:fixed!important;
  left:8px!important;
  top:var(--hilal-menu-v94-top, calc(max(env(safe-area-inset-top, 0px), 48px) + 6px))!important;
  right:auto!important;
  bottom:auto!important;

  width:76px!important;
  height:42px!important;
  min-width:76px!important;
  min-height:42px!important;
  margin:0!important;
  padding:0 10px!important;

  display:none!important;
  align-items:center!important;
  justify-content:center!important;
  gap:5px!important;

  border:1.5px solid rgba(217,183,96,.78)!important;
  border-radius:14px!important;
  background:#176b48!important;
  color:#fffdf6!important;
  box-shadow:0 6px 16px rgba(23,107,72,.14)!important;

  font:950 14px/1 system-ui,-apple-system,"Segoe UI",Roboto,Arial,sans-serif!important;

  transform:none!important;
  transition