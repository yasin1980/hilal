
(function(){
  const items=[
    ['Bugün','☀️'],
    ['Takvim & Ezan','🗓️'],
    ['Genel Duyurular','📣'],
    ['Genel Yayınlar','📚'],
    ['Takip Ettiklerim','👥'],
    ['Favoriler','⭐'],
    ['Günlük Virdlerim','📿'],
    ['Sünneti İhya','🌿'],
    ['Öneri / Şikayet','💬'],
    ['Ayarlar','⚙️'],
    ['Kaza Namazlarım','☾']
  ];

  function normalize(){
    const cards=[...document.querySelectorAll('#guestMenu .guest-menu-grid > button, #guestMenu .guest-menu-grid > a')];
    cards.forEach(card=>{
      const raw=(card.textContent||'').replace(/\s+/g,' ').trim().toLocaleLowerCase('tr-TR');
      let found=items.find(([label])=>{
        const key=label.toLocaleLowerCase('tr-TR');
        return raw.includes(key) || (label==='Kaza Namazlarım' && card.id==='kazaNamazlarimMenuItem');
      });
      if(!found) return;

      const badges=[...card.querySelectorAll('[id$="Badge"],[id*="Badge"]')].map(x=>x.cloneNode(true));
      card.innerHTML='';

      const icon=document.createElement('span');
      icon.className='premium-menu-icon';
      icon.textContent=found[1];

      const label=document.createElement('span');
      label.className='premium-menu-label';
      label.textContent=found[0];

      card.append(icon,label);
      badges.forEach(b=>card.appendChild(b));
    });
  }

  if(document.readyState==='loading'){
    document.addEventListener('DOMContentLoaded',()=>setTimeout(normalize,550));
  }else{
    setTimeout(normalize,550);
  }

  document.addEventListener('click',function(e){
    if(e.target.closest && e.target.closest('#mainMenuTrigger')){
      setTimeout(normalize,140);
    }
  });
})();
</script>
<style id="sadece-kaza-namazlari-ust-ok-kaldir">
#kazaNamazlarimPage #kazaNamazlarimBack{
  display:none !important;
}
</style>
<style id="sadece-kaza-namazlari-dis-ustbar-kaldir">
#kazaNamazlarimPage > div:first-child{
  display:none !important;
}
#kazaNamazlarimPage > iframe{
  height:100% !important;
}
</style>
<script id="kaza-geri-takvim-namaz">
(function(){
  const pageId='kazaNamazlarimPage';

  function kazaAcik(){
    const p=document.getElementById(pageId);
    return p && getComputedStyle(p).display!=='none';
  }

  function kazaKapat(){
    const p=document.getElementById(pageId);
    if(p) p.style.display='none';
  }

  /* Kaza Namazlarım açıldığında tarayıcı/telefon geçmişine tek adım ekle */
  document.addEventListener('click',function(e){
    const item=e.target.closest && e.target.closest('#kazaNamazlarimMenuItem');
  