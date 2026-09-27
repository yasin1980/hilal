
async function hilalRefreshPrayerLocationPro(btn){
  if(!btn) btn=document.getElementById("prayerLocationRefreshBtn");
  try{
    btn?.classList.add("is-refreshing");
    const result=refreshPrayerLocation(true);
    if(result && typeof result.then==="function") await result;
  }catch(e){
    console.warn("Konum yenileme hatası:",e);
  }finally{
    setTimeout(()=>btn?.classList.remove("is-refreshing"),650);
  }
}
</script>
<script id="hilal-vakit-cikis-clean-v80-js">
(function(){
  function getTimes(){
    try{
      if(typeof __hilalPrayerCache!=="undefined" && __hilalPrayerCache && __hilalPrayerCache.times){
        return __hilalPrayerCache.times;
      }
    }catch(e){}
    const m=window.__hilalLivePrayerMap||null;
    if(!m) return nul