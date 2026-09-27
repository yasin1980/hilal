
(function(){
  function fixKaza(){
    const item=document.getElementById('kazaNamazlarimMenuItem');
    if(!item) return;
    item.innerHTML='<span style="font-size:27px;line-height:1;color:#f0cd76">☾</span><span>Kaza Namazlarım</span>';
  }

  function run(){
    fixKaza();
  }

  if(document.readyState==='loading'){
    document.addEventListener('DOMContentLoaded',()=>setTimeout(run,500));
  }else{
    setTimeout(run,500);
  }

  document.addEventListener('click',function(e){
    if(e.target.closest && e.target.closest('#mainMenuTrigger')){
      setTimeout(run,120);
    }
  });
})();
</script>
<style id="uc-nokta-menu-premium-v2-final">
/* =========================================================
   3 NOKTA MENÜSÜ — PREMIUM V2
   YALNIZCA guest-menu içindeki navigasyon kartlarına etki eder.
   ========================================================= */

#guestMenu .guest-menu-grid{
  display:grid !important;
  grid-template-columns:repeat(2,minmax(0,1fr)) !important;
  gap:10px !important;
  align-items:stretch !important;
}

/* Her kart tek ve eşit standart */
#guestMenu .guest-menu-grid > button,
#guestMenu .guest-menu-grid > a{
  position:relative !important;
  box-sizing:border-box !important;
  width:100% !important;
  min-width:0 !important;
  height:86px !important;
  min-height:86px !important;
  margin:0 !important;
  padding:13px 13px !important;

  display:grid !important;
  grid-template-columns:34px minmax(0,