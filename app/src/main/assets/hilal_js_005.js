
/* ===== v1.13.28 HATİM İSTATİSTİĞİ: CÜZ ALINDI + HATİM YAPILDI ===== */
function hatimMadeCount(p){
  if(!p || p.type!=="hatim") return 0;
  normalizeOwnerHatimRounds(p);
  return (p.rounds||[]).filter(r=>{
    const juz=r.juz||[];
    return juz.length===30 && juz.every(j=>!!j.userKey);
  }).length;
}
function hatimCompletedCount(p){
  if(!p || p.type!=="hatim") return 0;
  normalizeOwnerHatimRounds(p);
  return (p.rounds||[]).filter(r=>{
    const juz=r.juz||[];
    return juz.length===30 && juz.every(j=>j.done===true);
  }).length;
}

/* Hoca panelindeki katılım kartı */
function teacherParticipationCard(p){
  const t=participationTotals(p),pct=participationPct(p);
  const complete=p.type==="hatim"
    ? hatimCompletedCount(p)>0 && hatim