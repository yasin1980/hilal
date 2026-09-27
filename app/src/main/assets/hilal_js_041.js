
(function(){
'use strict';
const HOUR=60*60*1000, DAY=24*HOUR;
const POLICY={
  hilal_teacher_announcements_v1:7*DAY,
  hilal_publications_v1:24*HOUR,
  hilal_follower_participations_v1:30*DAY
};
const DUA_KINDS=new Set(['hilal_dua_request','hilal_zikir_request','hilal_yasin_request','hilal_hatim_request']);
const MIGRATION='hilal_expiry_migration_20260920_v1';
function read(k){try{const v=JSON.parse(localStorage.getItem(k)||'[]');return Array.isArray(v)?v:[]}catch(_){return []}}
function write(k,v){try{localStorage.setItem(k,JSON.stringify(v));document.dispatchEvent(new CustomEvent('hilal:data-changed',{detail:{key:k}}));}catch(_){}}
function stamp(x){
  const vals=[x.publishedAt,x.createdAt,x.updatedAt,x.date,x.timestamp,x.created,x.time];
  for(const v of vals){if(!v)continue;const n=typeof v==='number'?v:Date.parse(v);if(Number.isFinite(n)&&n>0)return n;}
  return Date.now();
}
function isGeneralDua(x){
  const k=String(x?.kind||'');
  return DUA_KINDS.has(k) || ['dua','zikir','yasin','hatim'].includes(String(x?.type||'').toLowerCase());
}
function isVisibleGeneral(x){
  const st=String(x?.status||'published').toLowerCase();
  return !['draft','deleted','removed','completed','expired','pending_approval'].includes(st);
}
function cleanKey(k,maxAge,oneTime24h){
  const a=read(k), now=Date.now(); let changed=false;
  const b=a.filter(x=>{
    if(k==='hilal_follower_participations_v1' && !isGeneralDua(x)) return true;
    if(!isVisibleGeneral(x)) return true;
    const age=now-stamp(x);
    const limit=(oneTime24h && k==='hilal_follower_participations_v1')?DAY:maxAge;
    if(age>=limit){changed=true;return false;}
    return true;
  });
  if(changed)write(k,b);
  return changed;
}
function refresh(){
  try{typeof syncStateFromStorage==='function'&&syncStateFromStorage()}catch(_){}
  try{typeof renderGeneralPublicationsFeed==='function'&&renderGeneralPublicationsFeed()}catch(_){}
  try{typeof renderSharedAnnouncements==='function'&&renderSharedAnnouncements()}catch(_){}
  try{window.hilalDuaParticipationUI?.renderGeneral?.()}catch(_){}
  try{window.hilalDuaParticipationUI?.renderMine?.()}catch(_){}
}
function run(){
 