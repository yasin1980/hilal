
const HILAL_ADMIN_CONTACT_KEY="hilal_admin_contact_v1";
function getAdminContactSettings(){
  try{return JSON.parse(localStorage.getItem(HILAL_ADMIN_CONTACT_KEY)||"null")||{name:"Hilâl Yönetimi",whatsapp:"",message:"Hesabınızla ilgili bilgi için yöneticiyle iletişime geçebilirsiniz.",published:false}}catch(e){return {name:"Hilâl Yönetimi",whatsapp:"",message:"",published:false}}
}
function normalizeWhatsappNumber(v){return String(v||"").replace(/\D/g,"").replace(/^0/,"90")}
function renderAdminContactSettings(){
  const c=getAdminContactSettings();
  const n=document.getElementById("adminContactName"), w=document.getElementById("adminContactWhatsapp"), m=document.getElementById("adminContactMessage"), p=document.getElementById("adminContactPublished");
  if(n)n.value=c.name||"Hilâl Yönetimi"; if(w)w.value=c.whatsapp||""; if(m)m.value=c.message||""; if(p)p.checked=!!c.published;
}
function saveAdminContactSettings(){
  if(currentUser?.role!=="admin")return;
  const data={name:(document.getElementById("adminContactName")?.value||"Hilâl Yönetimi").trim(),whatsapp:normalizeWhatsappNumber(document.getElementById("adminContactWhatsapp")?.value||""),message:(document.getElementById("adminContactMessage")?.value||"").trim(),published:!!document.getElementById("adminContactPublished")?.checked,updatedAt:new Date().toISOString()};
  localStorage.setItem(HILAL_ADMIN_CONTACT_KEY,JSON.stringify(data));
  if(typeof hilalIslamicAlert==="function") hilalIslamicAlert(data.published?"Yönetici iletişim bilgileri kaydedildi ve kullanıcılara yayınlandı.":"Yönetici iletişim bilgileri kaydedildi. Yayınlama şu an kapalı.","İletişim Ayarları");
}
function toggleAdminContactSettingsCard(){
  const b=document.getElementById("adminContactSettingsBody"), c=document.getElementById("adminContactSettingsChevron"); if(!b)return;
  const opening=b.classList.contains("hidden"); b.classList.toggle("hidden",!opening); if(c)c.textContent=opening?"⌃":"⌄"; if(opening)renderAdminContactSettings();
}
function showInactiveAccountNotice(account){
  const modal=document.getElementById("inactiveAccountModal"), msg=document.getElementById("inactiveAccountMessage"), contact=document.getElementById("inactiveAccountContact"), wa=document.getElementById("inactiveAccountWhatsapp");
  const state=(typeof normalizeAccountState==="function")?normalizeAccountState(account):String(account?.accountState||"suspended");
  const base=account?.accountMessage || (state==="removed"?"Bu hesap yönetici tarafından kaldırılmıştır. Hesaba erişim kapalıdır.":"Hesabınız yönetici tarafından geçici olarak pasife alınmıştır. Bu hesaba giriş yapılamaz.");
  if(msg)msg.textContent=base;
  const cfg=getAdminContactSettings();
  if(contact){
    if(cfg.published){contact.classList.remove("hidden");contact.innerHTML=`<strong>${esc(cfg.name||"Hilâl Yönetimi")}</strong>${cfg.message?`<div>${esc(cfg.message)}</div>`:""}${cfg.whatsapp?`<div style="margin-top:6px;font-weight:800">WhatsApp: +${esc(cfg.whatsapp)}</div>`:""}`;}
    else{contact.classList.add("hidden");contact.innerHTML="";}
  }
  if(wa){
    if(cfg.published && cfg.whatsapp){const text=encodeURIComponent(`Selamün aleyküm. Hilâl uygulamasında ${account?.name||"hesabım"} pasife alınmış görünüyor. Bilgi rica ediyorum.`);wa.href=`https://wa.me/${cfg.whatsapp}?text=${text}`;wa.classList.remove("hidden");}
    else{wa.classList.add("hidden");wa.href="#";}
  }
  modal?.classList.remove("hidden"); modal?.setAttribute("aria-hidden","false");
}
function closeInactiveAccountNotice(){const m=document.getElementById("inactiveAccountModal");m?.classList.add("hidden");m?.setAttribute("aria-hidden","true")}
</script>
<style id="hilal-v11335-passive-messaging-style">
.inactive-message-panel{margin-top:12px;padding:13px;border:1px solid rgba(212,175,55,.55);border-radius:16px;background:rgba(0,55,42,.32)}
.inactive-message-title{font-weight:900;color:#d8b65a;font-size:15px;margin-bottom:4px}.inactive-message-help{font-size:12px;opacity:.8;line-height:1.45;margin-bottom:8px}
.inactive-message-panel textarea{width:100%;box-sizing:border-box;min-height:88px;resize:vertical;border-radius:13px;border:1px solid rgba(212,175,55,.5);background:rgba(0,0,0,.22);color:#fff;padding:11px;font:inherit;outline:none}
.inactive-message-send{width:100%;margin-top:9px;border:0;border-radius:12px;padding:11px 12px;font-weight:900;background:#d8b65a;color:#082b22}.inactive-message-status{min-height:1