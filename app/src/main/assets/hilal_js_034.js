
(function(){
'use strict';

function getFeed(){
  return document.getElementById('hilalDuaFeedV55');
}

function feedOpen(){
  const feed=getFeed();
  return !!(feed && feed.classList.contains('hd55-open'));
}

function ensureButton(){
  let btn=document.getElementById('hilalGenelDuaBackV90E');
  if(btn) return btn;

  btn=document.createElement('button');
  btn.type='button';
  btn.id='hilalGenelDuaBackV90E';
  btn.setAttribute('aria-label','Geri');
  btn.innerHTML='<span class="v90e-arrow" aria-hidden="true">‹</span><span>Geri</span>';

  btn.addEventListener('click',function(e){
    e.preventDefault();
    e.stopPropagation();
    e.stopImmediatePropagation();

    const feed=getFeed();
    if(!feed) return;

    /* V89'un mevcut Genel Dua kapatma işlevini çalıştır */
    const existingClose=feed.querySelector('[data-v55-feed-close]');
    if(existingClose){
      existingClose.click();
    }else{
      feed.classList.remove('hd55-open');
    }

    /* Dua İstekleri ana sayfası açık kalsın */
    const main=document.getElementById('hilalDuaPageV2');
    if(main){
      main.classList.add('hdv51-open','hdv2-open');
      main.setAttribute('aria-hidden','false');
    }
    try{ document.body.dataset.hilalDuaOpen='1'; }catch(_){}

    sync();
  },true);

  document.body.appendChild(btn);
  return btn;
}

function sync(){
  const btn=ensureButton();
  btn.classList.toggle('is-open',feedOpen());
}

const mo=new MutationObserver(function(){
  requestAnimationFrame(sync);
});
mo.observe(document.documentElement,{
  subtree:true,
  childList:true,
  attributes:true,
  attributeFilter:['class']
});

document.addEventListener('DOMContentLoaded',sync,{once:true});
document.addEventListener('click',function(){requestAnimationFrame(sync)},true);
window.addEventListener('pageshow',sync);
setTimeout(sync,0);
})();
</script>


<style id="hilal-calendar-hide-back-v90g-css">
/* V90G — SADECE Takvim & Ezan ana sayfasında global Geri gizlenir */
body:has(#mainApp:not(.hidden) #calendarPage.active)
:not(:has(#hilalDuaPageV2.hdv51-open))
:not(:has(#hilalDuaPageV2.hdv2-open))
:not(:has(#hilalDuaFeedV55.hd55-open))
:not(:has(#hilalDuaMyPageV53.hdv53-open))
#hilalNavigationBack{
  display:none!important;
  visibility:hidden!important;
  opacity:0!important;
  pointer-events:none!important;
}
</style>
<script id="hilal-calendar-hide-back-v90g-js">
(function(){
  function isOpen(el){
    if(!el) return false;
    if(el.hidden || el.getAttribute('aria-hidden')==='true') return false;
    const s=getComputedStyle(el);
    return s.display!=='none' && s.visibility!=='hidden';
  }
  function specialOpen(){
    const d=document.getElementById('hilalDuaPageV2');
    const f=document.getElementById('hilalDuaFeedV55');
    const m=document.getElementById('hila