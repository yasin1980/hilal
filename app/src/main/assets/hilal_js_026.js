
(function(){
  const q=s=>document.querySelector(s);
  const qa=s=>[...document.querySelectorAll(s)];
  const txt=(el)=>el ? (el.textContent||"").trim() : "";

  const icons={
    "imsak":"🌙","güneş":"☀️","öğle":"◉","ikindi":"◐","akşam":"🌆","yatsı":"🌙"
  };

  function prayerSourceRows(){
    const rows=qa("#calendarPage .prayer-row");
    return rows.filter(r=>{
      const t=(r.innerText||"").toLocaleLowerCase("tr-TR");
      return /imsak|güneş|öğle|ikindi|akşam|yatsı/.test(t);
    }).slice(0,6);
  }

  function renderPrayerRows(){
    const target=q("#hilalRefPrayerRowsV1");
    if(!target)return;
    const rows=prayerSourceRows();
    if(!rows.length)return;

    target.innerHTML=rows.map((r,i)=>{
      const rawName=txt(r.querySelector(".prayer-name > span:first-child")) || txt(r.query