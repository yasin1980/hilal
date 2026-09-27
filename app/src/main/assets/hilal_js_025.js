
(function(){
  function candidates(){
    return [...document.querySelectorAll(
      '#qiblaCompass,#kibleCompass,#compass,[id*="qibla" i],[id*="kible" i],[class*="qibla-compass" i],[class*="kible-compass" i],[class*="pusula" i]'
    )];
  }
  function rootFor(el){
    return el.closest('[id*="qibla" i],[id*="kible" i],[class*="qibla" i],[class*="kible" i],[class*="compass" i],[class*="pusula" i]') || el;
  }
  function update(){
    try{
      candidates().forEach(el=>{
        const root=rootFor(el);
        const t=(root.innerText||"").toLocaleLowerCase("tr-TR");
        const aligned =
          root.classList.contains("aligned") ||
          root.classList.contains("is-aligned") ||
          root.classList.contains("qibla-found") ||
          root.classList.contains("kible-found") ||
     