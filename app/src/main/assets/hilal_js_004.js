
/* ===== v1.13.27 DUA/ZIKIR SERI HEDEF + ART/EKSI ===== */
const DHIKR_ROUND_OPEN_KEY="hilal_dhikr_round_open_v1";

function dhikrRoundOpenMap(){
  try{return JSON.parse(sessionStorage.getItem(DHIKR_ROUND_OPEN_KEY)||"{}")||{}}
  catch(e){return {}}
}
function setDhikrRoundOpen(pid,no,val){
  const m=dhikrRoundOpenMap();
  m[String(pid)+"::"+Number(no)]=!!val;
  sessionStorage.setItem(DHIKR_ROUND_OPEN_KEY,JSON.stringify(m));
}
function normalizeDhikrRounds(p){
  if(!p || p.type==="hatim") return p;
  if(!Array.isArray(p.rounds) || !p.rounds.length){
    const target=Math.max(0,Number(p.target||0));
    p.rounds=[{
      no:1,
      target:target,
      participants:p.participants||{},
      createdAt:p.createdAt||new Date().toISOString()
 