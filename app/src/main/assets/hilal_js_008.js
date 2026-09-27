window.HILAL_VERSION="1.13.41";</script>
<script id="hilal-v11344-feedback-system">
(function(){
  const KEY="hilal_feedback_threads_v1";
  let feedbackImageData="";

  function escF(v){
    if(typeof esc==="function")return esc(v);
    return String(v??"").replace(/[&<>"']/g,m=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[m]));
  }
  function loadF(){ try{return JSON.parse(localStorage.getItem(KEY)||"[]")}catch(e){return []} }
  function saveF(v){ localStorage.setItem(KEY,JSON.stringify(v)); updateFeedbackBadges(); }
  function nowF(){return new Date().toISOString()}
  function idF(){return (typeof uid==="function"?uid():"fb-"+Date.now()+"-"+Math.random().toString(36).slice(2,8))}
  function userKe