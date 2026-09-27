
(function(){
"use strict";
function installMiracV1427(){
  const card=document.querySelector("#calendarPage .hilal-prayer-head-card");
  if(!card)return;
  let el=document.getElementById("hilalMiracV1427");
  if(!el){
    el=document.createElement("div");
    el.id="hilalMiracV1427";
    el.className="hilal-mirac-v1427";
    el.setAttribute("aria-label","NAMAZ MÜMİNİN MİRACIDIR");
    el.innerHTML='<span class="orn">◆</span><span>NAMAZ MÜMİNİN MİRACIDIR</span><span class="orn">◆</span>';
    card.appendChild(el);
  }
}
document.addEventListener("DOMContentLoaded",()=>setTimeout(installMiracV1427,180));
document.addEventListener("click",()=>setTimeout(installMiracV1427,60),true);
})();
</script>
<script id="hilal-v1429-qibla-engine">
(function(){
"use strict";
con