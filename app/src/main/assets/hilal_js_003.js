
/* ===== v1.13.26 KATILIM ANA KART AKORDİYONU ===== */
const PARTICIPATION_GROUP_OPEN_KEY="hilal_participation_group_open_v2";

function participationGroupOpenMap(){
  try{return JSON.parse(sessionStorage.getItem(PARTICIPATION_GROUP_OPEN_KEY)||"{}")||{}}
  catch(e){return {}}
}
function toggleParticipationGroup(id,event){
  if(event)event.stopPropagation();
  const m=participationGroupOpenMap();
  const k=String(id||"");
  m[k]=!(m[k]===true);
  sessionStorage.setItem(PARTICIPATION_GROUP_OPEN_KEY,JSON.stringify(m));
  try{renderGuestFollowingPage()}catch(e){}
}

function guestParticipationCard(p){
  const t=participationTotals(p),pct=participationPct(p),u=String(userKey());
  const open=participationGroupOpenMap()[String(p.id)]===true