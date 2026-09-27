
/* ===== v1.13.25 HATİM: ALINDI -> KAPAN -> SAHİBİ AÇIP TAMAMLAR ===== */
const HATIM_OWNER_OPEN_KEY="hilal_hatim_owner_open_v1";

function hatimOwnerOpenMap(){
  try{return JSON.parse(sessionStorage.getItem(HATIM_OWNER_OPEN_KEY)||"{}")||{}}catch(e){return {}}
}
function setHatimOwnerOpen(pid,roundNo,val){
  const m=hatimOwnerOpenMap();
  m[pid+"::"+roundNo]=!!val;
  sessionStorage.setItem(HATIM_OWNER_OPEN_KEY,JSON.stringify(m));
}
function normalizeOwnerHatimRounds(p){
  if(!p||p.type!=="hatim")return p;
  if(!Array.isArray(p.rounds)||!p.rounds.length){
    const first=Array.isArray(p.juz)&&p.juz.length?p.juz:Array.from({length:30},(_,i)=>({
      no:i+1,userKey:"",userName:"",done:false,takenAt:null,doneAt:null
    }));
    p.