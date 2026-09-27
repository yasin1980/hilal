
/* ===== v1.13.19 TAKİPÇİ KATILIM MOTORU ===== */
const PARTICIPATIONS_KEY="hilal_follower_participations_v1";
let participationComposerType="hatim";

function allParticipations(){ return loadJSON(PARTICIPATIONS_KEY,[])||[]; }
function saveParticipations(items){
  saveJSON(PARTICIPATIONS_KEY,items);
  try{renderTeacherParticipations()}catch(e){}
  try{renderGuestFollowingPage()}catch(e){}
}
function participationUserName(){
  return currentUser?.name||currentUser?.fullName||currentUser?.displayName||"Katılımcı";
}
function openParticipationComposer(){
  if(currentUser?.role!=="teacher")return;
  participationComposerType="hatim";
  const m=document.getElementById("participationComposerModal");
  if(!m)return;
  m.classList.remove("hidden");
  const t=document.getElementById("partTitle"); if(t)t.value="Kur’an-ı Kerim Hatmi";
  const d=document.getElementById("partDescription"); if(d)d.value="";
  selectParticipationType("hatim");
}
function closeParticipationComposer(){
  document.getElementById("participationComposerModal")?.classList.add("hidden");
}
function selectParticipationType(type){
  participationComposerType=type==="dhikr"?"dhikr":"hatim";
  document.getElementById("partTypeHatim")?.classList.toggle("active",participationComposerType==="hatim");
  document.getElementById("partTypeDhikr")?.classList.toggle("active",participationComposerType==="dhikr");
  document.getElementById("partDhikrOptions")?.classList.toggle("hidden",participationComposerType!=="dhikr");
  const title=document.getElementById("partTitle");
  if(title && (!title.value || title.value==="Kur’an-ı Kerim Hatmi")){
    title.value=participationComposerType==="hatim"?"Kur’an-ı Kerim Hatmi":"";
  }
  renderParticipationDistributionFields();
}
function renderParticipationDistributionFields(){
  const mode=document.getElementById("partDistribution")?.value||"fixed";
  document.getElementById("partFixedWrap")?.classList.toggle("hidden",mode!=="fixed");
  document.getElementById("partChoicesWrap")?.classList.toggle("hidden",mode!=="choices");
}
function publishParticipation(){
  if(currentUser?.role!=="teacher")return;
  const title=(document.getElementById("partTitle")?.value||"").trim();
  const description=(document.getElementById("partDescription")?.value||"").trim();
  if(!title){alert("Lütfen katılım başlığını yazın.");return}

  const now=new Date().toISOString();
  const item={
    id:"part_"+Date.now()+"_"+Math.random().toString(36).slice(2,7),
    ownerId:String(currentUser.key||""),
    ownerName:participationUserName(),
    type:participationComposerType,
    title,description,
    audience:"followers",followerOnly:true,status:"published",
    approvalDecision:"follower_private_auto",
    publishedAt:now,createdAt:now,
    participants:{}
  };

  if(item.type==="hatim"){
    item.target=30;
    item.distribution="juz";
    item.juz=Array.from({length:30},(_,i)=>({
      no:i+1,userKey:"",userName:"",done:false,takenAt:null,doneAt:null
    }));
  }else{
    const rawTarget=parseInt(document.getElementById("partTarget")?.value||"0",10);
    item.target=Number.isFinite(rawTarget)&&rawTarget>0?rawTarget:0;
    item.distribution=document.getElementById("partDistribution")?.value||"fixed";
    item.fixed=Math.max(1,parseInt(document.getElementById("partFixed")?.value||"1",10)||1);
    item.choices=String(document.getElementById("partChoices")?.value||"")
      .split(",").map(x=>parseInt(x.trim(),10)).filter(x=>x>0);
  }

  const items=allParticipations();
  items.unshift(item);
  saveParticipations(items);
  closeParticipationComposer();
  alert("Katılım takipçilerinize özel olarak doğrudan yayınlandı. Yönetici onayı gerekmez.");
}
function participationTotals(p){
  if(p.type==="hatim"){
    const list=p.juz||[];
    return {target:30,taken:list.filter(x=>x.userKey).length,done:list.filter(x=>x.done).length};
  }
  const entries=Object.values(p.participants||{});
  return {
    target:Number(p.target||0),
    taken:entries.reduce((s,x)=>s+Number(x.taken||0),0),
    done:entries.reduce((s,x)=>s+Number(x.done||0),0)
  };
}
function participationPct(p){
  const t=participationTotals(p);
  const base=t.target||t.taken||1;
  return Math.max(0,Math.min(100,Math.round((t.done/base)*100)));
}
function teacherParticipationCard(p){
  const t=participationTotals(p), pct=participationPct(p);
  const complete=t.target>0 && t.done>=t.target;
  return `<article class="participation-card private-card">
    <div class="participation-card-head">
      <div>
        <small>${p.type==="hatim"?"📖 HATİM":"🤲 DUA • ZİKİR • SÛRE"}</small>
        <h3>${esc(p.title)}</h3>
        <small>${esc(p.description||"Takipçilere özel katılım")}</small>
      </div>
      <span class="participation-badge">${complete?"✅ Tamamlandı":p.status==="published"?"🔒 Direkt Yayında":"⏸ Pasif"}</span>
    </div>
    <div class="participation-progress"><i style="width:${pct}%"></i></div>
    <div class="participation-stats">
      <span>${p.type==="hatim"?"Cüz":"Hedef"}<b>${t.target?Number(t.target).toLocaleString("tr-TR"):"Hedefsiz"}</b></span>
      <span>${p.type==="hatim"?"Alındı":"Dağıtılan"}<b>${Number(t.taken).toLocaleString("tr-TR")}</b></span>
      <span>Tamamlanan<b>${Number(t.done).toLocaleString("tr-TR")}</b></span>
    </div>
    <div class="participation-actions">
      <button type="button" onclick="toggleParticipationActive('${p.id}')">${p.status==="published"?"⏸ Pasife Al":"▶ Aktif Et"}</button>
      <button type="button" onclick="deleteParticipation('${p.id}')">🗑 Sil</button>
    </div>
  </article>`;
}
function renderTeacherParticipations(){
  const root=document.getElementById("teacherParticipationList");
  if(!root||currentUser?.role!=="teacher")return;
  const mine=allParticipations().filter(p=>String(p.ownerId)===String(currentUser.key||""));
  root.innerHTML=mine.length?mine.map(teacherParticipationCard).join(""):"";
}
function toggleParticipationActive(id){
  const items=allParticipations(),p=items.find(x=>x.id===id);
  if(!p||String(p.ownerId)!==String(currentUser?.key||""))return;
  p.status=p.status==="published"?"paused":"published";
  saveParticipations(items);
}
function deleteParticipation(id){
  const items=allParticipations();
  const p=items.find(x=>x.id===id);
  if(!p||String(p.ownerId)!==String(currentUser?.key||""))return;
  if(!confirm("Bu katılımı silmek istiyor musunuz?"))return;
  saveParticipations(items.filter(x=>x.id!==id));
}
function guestFollowingParticipationItems(){
  return allParticipations().filter(p=>
    p.status==="published" &&
    p.followerOnly===true &&
    currentUserFollowsTeacherOwner(p.ownerId)
  );
}
function takeJuz(pid,no){
  const items=allParticipations(),p=items.find(x=>x.id===pid);
  if(!p||!currentUserFollowsTeacherOwner(p.ownerId))return;
  const j=(p.juz||[]).find(x=>Number(x.no)===Number(no));
  if(!j)return;
  const u=String(userKey());
  if(j.userKey && String(j.userKey)!==u){alert("Bu cüz başka bir katılımcı tarafından alınmış.");return}
  if(String(j.userKey)===u){
    if(j.done){alert("Tamamlanmış cüz bırakılamaz.");return}
    j.userKey="";j.userName="";j.takenAt=null;
  }else{
    j.userKey=u;j.userName=participationUserName();j.takenAt=new Date().toISOString();j.done=false;
  }
  saveParticipations(items);
}
function completeJuz(pid,no){
  const items=allParticipations(),p=items.find(x=>x.id===pid);
  if(!p)return;
  const j=(p.juz||[]).find(x=>Number(x.no)===Number(no));
  if(!j||String(j.userKey)!==String(userKey()))return;
  j.done=true;j.doneAt=new Date().toISOString();
  saveParticipations(items);
}
function remainingCapacity(p){
  const t=participationTotals(p);
  return t.target>0?Math.max(0,t.target-t.taken):Infinity;
}
function takeParticipationAmount(pid,amount){
  const items=allParticipations(),p=items.find(x=>x.id===pid);
  if(!p||!currentUserFollowsTeacherOwner(p.ownerId))return;
  amount=Math.max(0,parseInt(amount,10)||0);
  if(!amount){alert("Geçerli bir miktar girin.");return}
  const cap=remainingCapacity(p);
  if(Number.isFinite(cap)&&amount>cap){
    alert(`Kalan hedef ${cap.toLocaleString("tr-TR")} adettir.`);
    return;
  }
  const u=String(userKey());
  p.participants=p.participants||{};
  const e=p.participants[u]||{userKey:u,userName:participationUserName(),taken:0,done:0};
  e.taken=Number(e.taken||0)+amount;
  e.userName=participationUserName();
  p.participants[u]=e;
  saveParticipations(items);
}
function completeParticipationAmount(pid,amount){
  const items=allParticipations(),p=items.find(x=>x.id===pid);
  if(!p)return;
  const u=String(userKey()),e=p.participants?.[u];
  if(!e)return;
  const remaining=Math.max(0,Number(e.taken||0)-Number(e.done||0));
  amount=Math.max(0,parseInt(amount,10)||0);
  if(!amount||amount>remaining){
    alert(`Tamamlanmayı bekleyen miktar ${remaining.toLocaleString("tr-TR")} adettir.`);
    return;
  }
  e.done=Number(e.done||0)+amount;
  saveParticipations(items);
}
function completeAllParticipation(pid){
  const items=allParticipations(),p=items.find(x=>x.id===pid);
  if(!p)return;
  const e=p.participants?.[String(userKey())];
  if(!e)return;
  e.done=Number(e.taken||0);
  saveParticipations(items);
}
function guestParticipationCard(p){
  const t=participationTotals(p),pct=participationPct(p),u=String(userKey());
  let controls="";
  if(p.type==="hatim"){
    const mine=(p.juz||[]).filter(j=>String(j.userKey)===u);
    controls=`${mine.length?`<div class="my-participation-note">Benim aldıklarım: ${mine.map(j=>`${j.no}. Cüz ${j.done?"✅":"📖"}`).join(" • ")}</div>`:""}
    <div class="cuz-grid">${(p.juz||[]).map(j=>{
      const my=String(j.userKey)===u;
      let click="";
      if(my && !j.done) click=`completeJuz('${p.id}',${j.no})`;
      else if(!j.userKey) click=`takeJuz('${p.id}',${j.no})`;
      return `<button class="cuz-btn ${j.done?"done":j.userKey?"taken":""} ${my?"mine":""}" type="button"
        ${j.userKey&&!my?"disabled":""}
        ${click?`onclick="${click}"`:""}>
        ${j.no}<br>${j.done?"✓":my?"Tamamla":j.userKey?"Alındı":"Al"}
      </button>`;
    }).join("")}</div>`;
  }else{
    const e=p.participants?.[u]||{taken:0,done:0};
    const remain=Math.max(0,Number(e.taken)-Number(e.done));
    let take="";
    if(p.distribution==="fixed"){
      take=`<button class="amount-choice primary" onclick="takeParticipationAmount('${p.id}',${Number(p.fixed||1)})">${Number(p.fixed||1).toLocaleString("tr-TR")} Al</button>`;
    }else if(p.distribution==="choices"){
      take=(p.choices||[]).map(n=>`<button class="amount-choice" onclick="takeParticipationAmount('${p.id}',${n})">${Number(n).toLocaleString("tr-TR")}</button>`).join("");
    }else{
      take=`<div class="participation-free-row"><input id="free_${p.id}" type="number" min="1" inputmode="numeric" placeholder="Alacağınız adet"><button class="amount-choice primary" onclick="takeParticipationAmount('${p.id}',document.getElementById('free_${p.id}').value)">Adet Al</button></div>`;
    }
    controls=`<div class="amount-choices">${take}</div>
      ${Number(e.taken)>0?`<div class="my-participation-note">Benim katılımım: ${Number(e.taken).toLocaleString("tr-TR")} alındı • ${Number(e.done).toLocaleString("tr-TR")} tamamlandı • ${remain.toLocaleString("tr-TR")} kaldı</div>`:""}
      ${remain>0?`<div class="participation-complete-row"><input id="done_${p.id}" type="number" min="1" max="${remain}" inputmode="numeric" placeholder="Tamamladığım adet"><button class="amount-choice" onclick="completeParticipationAmount('${p.id}',document.getElementById('done_${p.id}').value)">✓ Tamamla</button><button class="amount-choice primary" onclick="completeAllParticipation('${p.id}')">Tümünü Tamamladım</button></div>`:""}`;
  }

  return `<article class="participation-card private-card">
    <div class="participation-card-head">
      <div>
        <small>${p.type==="hatim"?"📖 HATİM":"🤲 DUA • ZİKİR • SÛRE"} • ${esc(p.ownerName||"Hoca")}</small>
        <h3>${esc(p.title)}</h3>
        <small>${esc(p.description||"")}</small>
        ${p.type==="hatim"?`<div class="hatim-main-guide">
          <span>📥 <b>1. tık:</b> Cüzü okumak için aldım</span>
          <span>✅ <b>2. tık:</b> Cüzü okudum / tamamladım</span>
          <small>Bir takipçi birden fazla cüz alabilir; aynı hatme birden fazla takipçi de katılabilir.</small>
        </div>`:""}
      </div>
      <span class="participation-badge">🔒 Takipçilere Özel</span>
    </div>
    <div class="participation-progress"><i style="width:${pct}%"></i></div>
    <div class="participation-stats">
      <span>${p.type==="hatim"?"Cüz":"Hedef"}<b>${t.target?Number(t.target).toLocaleString("tr-TR"):"Hedefsiz"}</b></span>
      <span>${p.type==="hatim"?"Alındı":"Dağıtılan"}<b>${Number(t.taken).toLocaleString("tr-TR")}</b></span>
      <span>Tamamlandı<b>${Number(t.done).toLocaleString("tr-TR")}</b></span>
    </div>
    ${controls}

  </article>`;
}

/* Hoca Takip Edenlerim sayfası açıldığında katılımları da yenile */
document.addEventListener("click",function(e){
  const b=e.target.closest?.("[data-tpage='teacherFollowersPage'],[onclick*=\"teacherFollowersPage\"]");
  if(b)setTimeout(()=>{try{renderTeacherParticipations()}catch(err){}},0);
});
window.addEventListener("storage",function(e){
  if(e.key===PARTICIPATIONS_KEY){
    try{renderTeacherParticipations()}catch(err){}
    try{renderGuestFollowingPage()}catch(err){}
  }
});
</script>
<script>
/* ===== v1.13.20 HATİM TURU / SATIR SİSTEMİ ===== */
const HATIM_OPEN_KEY="hilal_hatim_open_rows_v1";
const PARTICIPATION_OPEN_KEY="hilal_participation_open_cards_v1";

function partOpenMap(key){
  try{return JSON.parse(sessionStorage.getItem(key)||"{}")||{}}catch(e){return {}}
}
function toggleParticipationCard(id){
  const m=partOpenMap(PARTICIPATION_OPEN_KEY);
  m[id]=!m[id]; sessionStorage.setItem(PARTICIPATION_OPEN_KEY,JSON.stringify(m));
  try{renderGuestFollowingPage()}catch(e){}
}
function toggleHatimRound(pid,roundNo,event){
  if(event)event.stopPropagation();
  const m=partOpenMap(HATIM_OPEN_KEY),k=pid+"::"+roundNo;
  m[k]=!(m[k]===true);
  sessionStorage.setItem(HATIM_OPEN_KEY,JSON.stringify(m));
  try{renderGu