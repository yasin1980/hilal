
const HILAL_PASSIVE_MESSAGES_KEY="hilal_passive_account_messages_v1";
let hilalPassiveAccountContext=null;
function passiveMessages(){try{return JSON.parse(localStorage.getItem(HILAL_PASSIVE_MESSAGES_KEY)||"[]")||[]}catch(e){return []}}
function savePassiveMessages(v){localStorage.setItem(HILAL_PASSIVE_MESSAGES_KEY,JSON.stringify(v||[]))}
function passiveAccountKey(a){return String(a?.id||`${a?.role||"user"}::${String(a?.name||"").trim().toLocaleLowerCase("tr")}`)}
function passiveThreadFor(a){const k=passiveAccountKey(a);return passiveMessages().filter(m=>m.accountKey===k).sort((x,y)=>new Date(x.createdAt)-new Date(y.createdAt))}
function latestPassiveAdminReply(a){const t=passiveThreadFor(a).filter(m=>m.from==="admin");return t.length?t[t.length-1]:null}
function sendPassiveAccountMessage(){
  const a=hilalPassiveAccountContext;if(!a)return;
  const t=document.getElementById("inactiveAccountMessageText"),st=document.getElementById("inactiveAccountSendStatus"),btn=document.getElementById("inactiveAccountSendBtn");
  const text=(t?.value||"").trim();if(!text){if(st)st.textContent="Lütfen mesajınızı yazın.";return}
  const list=passiveMessages();list.push({id:(typeof uid==="function"?uid():`pm-${Date.now()}`),accountKey:passiveAccountKey(a),accountId:a.id||"",role:a.role||"teacher",name:a.name||"",from:"user",message:text,createdAt:new Date().toISOString()});savePassiveMessages(list);
  if(btn){btn.disabled=true;btn.textContent="✓ Gönderildi"}if(st)st.textContent="Mesajınız yöneticiye gönderildi.";
  setTimeout(()=>{try{closeInactiveAccountNotice()}catch(e){} if(btn){btn.disabled=false;btn.textContent="Mesajı Gönder"}},450);
}
function togglePassiveThread(id){document.getElementById(`passiveThread-${id}`)?.classList.toggle("hidden")}
function sendPassiveAdminReply(accountId){
  let db=loadJSON(ADMIN_USERS_KEY,[]);const a=db.find(x=>String(x.id)===String(accountId));if(!a)return;
  const ta=document.getElementById(`passiveReply-${accountId}`);const text=(ta?.value||"").trim();if(!text){alert("Hocaya gönderilecek mesajı yazın.");return}
  const list=passiveMessages();list.push({id:(typeof uid==="function"?uid():`pm-${Date.now()}`),accountKey:passiveAccountKey(a),accountId:a.id||"",role:a.role||"teacher",name:a.name||"",from:"admin",message:text,createdAt:new Date().toISOString()});savePassiveMessages(list);if(ta)ta.value="";renderAccountManagement(a.role);
}
function clearPassiveThread(a){const k=passiveAccountKey(a);savePassiveMessages(passiveMessages().filter(m=>m.accountKey!==k))}
function passiveThreadHTML(a){
  const list=passiveThreadFor(a),userCount=list.filter(m=>m.from==="user").length;
  const msgs=list.length?list.map(m=>`<div class="passive-thread-msg ${m.from}"><div class="passive-thread-meta">${m.from==="admin"?"Yönetici":"Kullanıcı"} • ${new Date(m.createdAt).toLocaleString("tr-TR")}</div><div>${esc(m.message)}</div></div>`).join(""):`<div class="passive-thread-empty">Henüz kullanıcıdan mesaj gelmedi.</div>`;
  return `<button type="button" class="passive-thread-toggle" onclick="togglePassiveThread('${a.id}')">💬 Gelen Mesajlar${userCount?` (${userCount})`:""} • Aç / Kapat</button><div id="passiveThread-${a.id}" class="passive-thread-body hidden">${msgs}<textarea id="passiveReply-${a.id}" class="passive-thread-reply" placeholder="Hocaya cevap yazın..."></textarea><button type="button" class="passive-thread-send" onclick="sendPassiveAdminReply('${a.id}')">Mesajı Hocaya Gönder</button></div>`
}
// Pasif hesap bildirimi: iletişim + uygulama içi mesaj + son yönetici cevabı
window.showInactiveAccountNotice=function(account){
  hilalPassiveAccountContext=account||null;
  const modal=document.getElementById("inactiveAccountModal"),msg=document.getElementById("inactiveAccountMessage"),contact=document.getElementById("inactiveAccountContact"),wa=document.getElementById("inactiveAccountWhatsapp"),reply=document.getElementById("inactiveAdminReply"),ta=document.getElementById("inactiveAccountMessageText"),st=document.getElementById("inactiveAccountSendStatus"),btn=document.getElementById("inactiveAccountSendBtn");
  const state=(typeof normalizeAccountState==="function")?normalizeAccountState(account):String(account?.accountState||"suspended");
  const base=account?.accountMessage || (state==="removed"?"Bu hesap yönetici tarafından kaldırılmıştır. Hesaba erişim kapalıdır.":"Hesabınız yönetici tarafından geçici olarak pasife alınmıştır. Bu hesaba giriş yapılamaz.");if(msg)msg.textContent=base;
  const cfg=getAdminContactSettings();if(contact){if(cfg.published){contact.classList.remove("hidden");contact.innerHTML=`<strong>${esc(cfg.name||"Hilâl Yönetimi")}</strong>${cfg.message?`<div>${esc(cfg.message)}</div>`:""}${cfg.whatsapp?`<div style="margin-top:6px;font-weight:800">WhatsApp: +${esc(cfg.whatsapp)}</div>`:""}`}else{contact.classList.add("hidden");contact.innerHTML=""}}
  if(wa){if(cfg.published&&cfg.whatsapp){const text=encodeURIComponent(`Selamün aleyküm. Hilâl uygulamasında ${account?.name||"hesabım"} pasife alınmış görünüyor. Bilgi rica ediyorum.`);wa.href=`https://wa.me/${cfg.whatsapp}?text=${text}`;wa.classList.remove("hidden")}else{wa.classList.add("hidden");wa.href="#"}}
  const last=latestPassiveAdminReply(account);if(reply){if(last){reply.classList.remove("hidden");reply.innerHTML=`<strong>📩 Yöneticiden Mesaj</strong><div>${esc(last.message)}</div>`}else{reply.classList.add("hidden");reply.innerHTML=""}}
  if(ta)ta.value="";if(st)st.textContent="";if(btn){btn.disabled=false;btn.textContent="Mesajı Gönder"}
  modal?.classList.remove("hidden");modal?.setAttribute("aria-hidden","false");
}
window.closeInactiveAccountNotice=function(){hilalPassiveAccountContext=null;const m=document.getElementById("inactiveAccountModal");m?.classList.add("hidden");m?.setAttribute("aria-hidden","true")}
// Hesap yönetiminde pasif kullanıcının mesajlarını aynı kart içinde açılır/kapanır göster.
window.renderAccountManagement=function(role){
  adminUsers=loadJSON(ADMIN_USERS_KEY,[]);const input=document.getElementById(role==="guest"?"guestAccountSearch":"teacherAccountSearch");const q=(input?.value||"").trim().toLocaleLowerCase("tr");
  const list=adminUsers.filter(u=>u.role===role&&u.status==="approved").filter(u=>!q||(u.name||"").toLocaleLowerCase("tr").includes(q)||(u.email||"").toLocaleLowerCase("tr").includes(q)).sort((a,b)=>a.name.localeCompare(b.name,"tr",{sensitivity:"base"}));
  const root=document.getElementById(role==="guest"?"adminGuestAccountsList":"adminTeacherAccountsList");if(!root)return;
  root.innerHTML=list.length?list.map(u=>{const state=normalizeAccountState(u),label=state==="active"?"Aktif":state==="suspended"?"Pasif / Bekletiliyor":"Kaldırıldı";return `<article class="account-status-card">${state==="suspended"?`<button class="account-delete-top" type="button" onclick="openDeleteAccountConfirm('${u.id}','${role}')">Sil</button>`:""}<div style="min-width:0;flex:1"><div class="admin-user-name">${esc(u.name)}</div><div class="admin-user-meta">${esc(u.email||"")}</div><span class="account-badge ${state}">${label}</span>${u.accountMessage?`<div class="account-note">${esc(u.accountMessage)}</div>`:""}${state==="suspended"?passiveThreadHTML(u):""}<div class="account-status-actions">${state!=="active"?`<button class="ok" onclick="setAccountState('${u.id}','active')">Aktif Et</button>`:""}${state!=="suspended"?`<button class="warn" onclick="setAccountState('${u.id}','suspended')">Pasife Al</button>`:""}${state!=="removed"?`<button class="danger" onclick="setAccountState('${u.id}','removed')">Hesabı Kaldır</button>`:""}</div></div></article>`}).join(""):'<div class="admin-empty">Bu bölümde onaylı hesap yok.</div>';
}
window.setAccountState=function(id,state){
  adminUsers=loadJSON(ADMIN_USERS_KEY,[]);const u=adminUsers.find(x=>String(x.id)===String(id));if(!u)return;const prev=normalizeAccountState(u);u.accountState=state;u.accountStateUpdatedAt=new Date().toISOString();
  if(state==="suspended"){if(prev!=="suspended")clearPassiveThread(u);u.accountMessage="Hesabınız yönetici tarafından geçici olarak pasife alınmıştır. Nedenini öğrenmek için bu ekrandan yöneticiye mesaj gönderebilirsiniz."}
  else if(state==="removed"){u.accountMessage="Bu hesap yönetici tarafından kaldırılmıştır. Hesaba erişim kapatılmıştır. Yeniden erişim için yönetici onayı gerekir."}
  else{u.accountMessage="";clearPassiveThread(u)}
  saveJSON(ADMIN_USERS_KEY,adminUsers);
  // kayıtlı hoca/misafir kaydını da senkronla
  try{let regs=loadJSON("hilal_demo_registrations_v1",[]);const r=regs.find(x=>String(x.id)===String(u.id)||(x.role===u.role&&String(x.name||"").toLocaleLowerCase("tr")===String(u.name||"").toLocaleLowerCase("tr")));if(r){r.accountState=state;r.accountMessage=u.accountMessage;saveJSON("hilal_demo_registrations_v1",regs)}}catch(e){}
  renderAccountManagement(u.role);try{renderApprovedPage(u.role)}catch(e){}
}
</script>
<script id="hilal-v11336-suspended-teacher-flow-legacy-disabled" type="text/plain">\n(function(){\n  function db(){ try{return loadJSON(ADMIN_USERS_KEY,[])||[]}catch(e){return []} }\n  function msgdb(){ try{return passiveMessages()||[]}catch(e){return []} }\n  function saveMsgs(v){ try{savePassiveMessages(v)}catch(e){} }\n  function key(a){return passiveAccountKey(a)}\n  function thread(a){return msgdb().filter(m=>m.accountKey===key(a)).sort((x,y)=>new Date(x.createdAt)-new Date(y.createdAt))}\n  function unreadAdminCount(a){return thread(a).filter(m=>m.from==='admin'&&!m.readByUser).length}\n  function unreadUserCount(a){return thread(a).filter(m=>m.from==='user'&&!m.readByAdmin).length}\n  function esc2(v){return typeof esc==='function'?esc(v):String(v||'').repla