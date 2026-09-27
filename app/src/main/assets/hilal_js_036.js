
(function(){
  'use strict';
  function applyV110Alignment(){
    ['reminderGregorianWeekdayValue','reminderGregorianYearValue'].forEach(function(id){
      var el=document.getElementById(id);
      var field=el&&el.closest('.field');
      if(field) field.classList.add('hilal-v110-legacy-hidden');
    });
    var dual=document.querySelector('.reminder-dual-date');
    if(dual) dual.classList.add('hilal-v110-dual-aligned');
  }
  if(document.readyState==='loading') document.addEventListener('DOMContentLoaded',applyV110Alignment,{once:true});
  else applyV110Alignment();
  var oldOpen=window.openReminderModal;
  if(typeof oldOpen==='function' && !oldOpen.__hilalV110){
    var wrapped=function(){var r=oldOpen.apply(this,arguments);setTimeout(applyV110Alignment,0);return r};
    wrapped.__hilalV110=true;window.openReminderModal=wrapped;
  }
})();
</script>
<style id="hilal-page-entrance-motion-v11