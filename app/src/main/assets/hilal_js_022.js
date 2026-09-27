
(function(){
  function normalizeOne(btn){
    if(!btn || btn.dataset.premiumV2==='1') return;

    /* Kaza kartı dinamik oluşturulduğu için metnini standartlaştır */
    if(btn.id==='kazaNamazlarimMenuItem'){
      btn.textContent='☾ Kaza Namazlarım';
    }

    /* Badge'leri koruyup görünür metni ayır */
    const badgeEls=[...btn.querySelectorAll('[id$="Badge"], [id*="Badge"]')];
    badgeEls.forEach(x=>x.remove());

    let raw=(btn.textContent||'').replace(/\s+/g,' ').trim();
    let icon='•', label=raw;

    const first=Array.from(raw)[0];
    if(first && !/[A-Za-zÇĞİÖŞÜçğıöşü0-9]/.test(first)){
      const arr=Array.from(raw);
      icon=arr.shift();
      label=arr.join('').trim();
    }

    if(btn.id==='kazaNamazlarimMenuItem'){
      icon='☾';
      label='Kaza Namazlarım';
    }

    btn.innerHTML='';
    const i=document.createElement('span');
    i.className='premium-menu-icon';
    i.textContent=icon;
    const l=document.createElement('span');
    l.className='premium-menu-label';
    l.textContent=label;
    btn.append(i,l);

    /* Badge varsa geri ekle; işlevini bozma */
    badgeEls.forEach(b=>btn.appendChild(b));
    btn.dataset.premiumV2='1';
  }

  function normalizeAll(){
    document.querySelectorAll('#guestMenu .guest-menu-grid > button, #guestMenu .guest-menu-grid > a')
      .forEach(normalizeOne);
  }

  if(document.readyState==='loading'){
    document.addEventListener('DOMContentLoaded',()=>setTimeout(normalizeAll,550));
  }else{
    setTimeout(normalizeAll,550);
  }

  document.addEventListener('click',function(e){
    if(e.target.closest && e.target.closest('#mainMenuTrigger')){
      setTimeout(normalizeAll,140);
    }
  });
})();
</script>
<style id="uc-nokta-menu-premium-v3-alt-alta">
#guestMenu .guest-menu-grid{
  display:grid !important;
  grid-template-columns:1fr !important;
  gap:9px !important;
  align-items:stretch !important;
}

#guestMenu .guest-menu-grid > button,
#guestMenu .guest-menu-grid > a{
  position:relative !important;
  box-sizing:border-box !important;
  width:100% !important;
  min-width:0 !important;
  height:72px !important;
  min-height:72px !important;
  margin:0 !important;
  padding:10px 14px !important;
  display:grid !important;
  grid-template-columns:46px minmax(0,1fr) 20px !important;
  align-items:center !important;
  column-gap:12px !important;
  border:1px solid rgba(221,185,94,.54) !important;
  border-radius:18px !important;
  background:
    radial-gradient(circle at 92% 15%, rgba(240,202,108,.055), transparent 34%),
    linear-gr