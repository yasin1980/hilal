
(function(){
'use strict';
let stream=null;
function q(){return document.getElementById('hilalQiblaV1428')}
function stopCamera(){if(stream){stream.getTracks().forEach(t=>{try{t.stop()}catch(e){}});stream=null}const root=q();if(root)root.classList.remove('qcam-live')}
async function startCamera(){
 const root=q(); if(!root)return; const msg=root.querySelector('.qcam-msg');
 try{
  if(!navigator.mediaDevices||!navigator.mediaDevices.getUserMedia)throw new Error('unsupported');
  if(stream){root.classList.add('qcam-live');return}
  if(msg)msg.textContent='Kamera açılıyor…';
  stream=await navigator.mediaDevices.getUserMedia({video:{facingMode:{ideal:'environment'},width:{ideal:1280},height:{ideal:720}},audio:false});
  const v=root.querySelector('.qcam-video'); if(v){v.srcObject=stream;await v.play().catch(()=>{});}
  root.classList.add('qcam-live'); if(msg)msg.textContent='';
 }catch(e){
  stopCamera();
  if(msg)msg.textContent=(e&&e.name==='NotAllowedError')?'Kamera izni kapalı. Telefon Ayarlar > Hilâl > İzinler > Kamera bölümünden izin verin.':'Kamera açılamadı. Kamera iznini kontrol edin.';
 }
}
function install(){
 const root=q(); if(!root||root.dataset.cameraInstalled==='1')return; root.dataset.cameraInstalled='1';
 const v=document.createElement('video');v.className='qcam-video';v.autoplay=true;v.muted=true;v.playsInline=true;v.setAttribute('aria-hidden','true');
 const shade=document.createElement('div');shade.className='qcam-shade';
 root.insertBefore(v,root.firstChild);root.insertBefore(shade,v.nextSibling);
 const btn=document.createElement('button');btn.type='button';btn.className='qcam-open';btn.textContent='Kamerayı Aç';btn.addEventListener('click',e=>{e.stopPropagation();startCamera()});
 const msg=document.createElement('div');msg.className='qcam-msg';msg.textContent='Kıbleyi kamera üzerinde görmek için Kamerayı Aç’a dokunun.';
 root.appendChild(btn);root.appendChild(msg);
 const compass=document.getElementById('q1428CompassTap');if(compass)compass.addEventListener('click',()=>startCamera(),{passive:true});
}
document.addEventListener('DOMContentLoaded',()=>setTimeout(install,500));
document.addEventListener('click',()=>setTimeout(install,80),true);
window.addEventListener('hilalCameraDenied',()=>{const root=q(),m=root&&root.querySelector('.qcam-msg');if(m)m.textContent='Kamera izni verilmedi. Telefon ayarlarından Kamera iznini açın.'});
document.addEventListener('visibilitychange',()=>{if(document.hidden)stopCamera()});
window.addEventListener('pagehide',stopCamera);
})();
</script>
<!-- HİLÂL: Dua İstekleri iç kütüphanesi tamamen kaldırıldı. Yalnız navigasyon butonu yer tutucu olarak korunur. Yeni içerik daha sonra temiz bağlantıyla eklenecek. -->
<!-- V59: V56 hızlı taban + çalışan tek Dua iframe/Firebase köprüsü -->
<script id="hilal-v56-dua-firebase-canonical-bridge">
(function(){
'use strict';
/* V56: Dua İstekleri için tek ortak kaynak.
   Firebase yolu: /shared/hilal_dua_requests_v2/requests/{requestId}
   iframe localStorage yalnız cihaz önbelleğidir. */
var ROOT=(window.HILAL_FIREBASE_DATABASE_URL||'https://hilal-2b1a5-default-rtdb.europe-west1.firebasedatabase.app')
  +'/shared/hilal_dua_requests_v2/requests';
var frame=null, stream=null, pollTimer=0, pulling=false, pushQueue=Promise.resolve();
var lastOwnedIds=new Set();

function f(){ return document.getElementById('hilalDuaV165Frame'); }
function profile(){
  try{
    var u=(typeof currentUser!=='undefined'&&currentUser)?currentUser:{};
    var p=(typeof window.currentAccountPr