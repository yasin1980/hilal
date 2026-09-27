
(function(){
  "use strict";
  function items(){try{return Array.isArray(allParticipations())?allParticipations():[]}catch(_){try{return JSON.parse(localStorage.getItem("hilal_follower_participations_v1")||"[]")}catch(e){return []}}}
  function me(){try{return String(userKey())}catch(_){return String(currentUser?.key||"anonymous")}}
  function name(){try{return participationUserName()}catch(_){return currentUser?.name||currentUser?.fullName||currentUser?.displayName||"Misafir"}}
  function save(a){try{localStorage.setItem("hilal_follower_participations_v1",JSON.stringify(a))}catch(_){} try{document.dispatchEvent(new CustomEvent("hilal:data-changed",{detail:{key:"hilal_follower_participations_v1"}}))}catch(_){}}
  function state(id){const a=items(),it=a.find(x=>x?.kind==="hilal_dua_request"&&String(x.id)===String(id)),p=it?.participants?.[me()]; return {a,it,p};}
  function paint(btn){const {p}=state(btn.dataset.hdtiPray); if(!p){btn.textContent="🤲 Dua al";btn.setAttribute("aria-pressed","false");return} const done=Number(p.done||0)>=Number(p.taken||1); btn.textContent=done?"✓ Dua tamamlandı":"✓ Dua ettim";btn.setAttribute("aria-pressed",done?"false":"true");btn.classList.toggle("is-prayed",!done)}
  function paintAll(){document.querySelectorAll('#hilalDuaPageV2 [data-hdti-pray]').forEach(paint)}
  document.addEventListener("click",function(e){
    const btn=e.target.closest?.('#hilalDuaPageV2 [data-hdti-pray]'); if(!btn)return;
    e.preventDefault();e.stopPropagation();e.stopImmediatePropagation();
    const id=btn.dataset.hdtiPray,{a,it,p}=state(id); if(!it)return; it.participants=it.participants&&typeof it.participants==="object"?it.participants:{}; const u=me();
    if(!p){it.participants[u]={userKey:u,userName:name(),taken:1,done:0,prayed:false,takenAt:new Date().toISOString()};}
    else if(Number(p.done||0)<Number(p.taken||1)){p.done=Number(p.taken||1);p.prayed=true;p.prayedAt=new Date().toISOString();}
    else{return}
    it.updatedAt=new Date().toISOString(); save(a); paint(btn);
    try{if(window.hilalGeneralAldiklarimGetMode?.()==="mine")setTimeout(()=>window.hilalGeneralAldiklarimApply?.(),30)}catch(_){}
  },true);
  new MutationObserver(()=>paintAll()).observe(document.getElementById("hilalDuaPageV2")||document.body,{childList:true,subtree:true});
  setTimeout(paintAll,300);
