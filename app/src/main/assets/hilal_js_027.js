
(function(){
  function q(s){return document.querySelector(s)}
  function bindRefresh(){
    const btn=q("#hilalRefLocationRefreshV2");
    if(!btn || btn.dataset.bound==="1")return;
    btn.dataset.bound="1";
    btn.addEventListener("click",function(e){
      e.preventDefault();
      e.stopPropagation();
      const original=q("#calendarPage #prayerLocationRefreshBtn");
      if(original){
        this.classList.add("is-refreshing");
        try{original.click()}catch(err){}
        setTimeout(()=>this.classList.remove("is-refreshing"),1200);
      }
    });
  }

  /* Görsel üstündeki bütün değerleri gerçek DOM ile sürekli aynı tut */
  function syncLive(){
    try{
      const h=q("#calendarPage .hilal-hijri-card h2");
      const g=q("#calendarPage .hilal-greg-date");
      const c=q("#calenda