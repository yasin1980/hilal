
(function(){
  "use strict";

  const SENTINEL_KEY = "__hilal_app_guard_v94";

  function appIsOpen(){
    const main = document.getElementById("mainApp");
    return !!(main && !main.classList.contains("hidden"));
  }

  function closeTransientUi(){
    try{ closeGuestMenu(); }catch(e){}
    try{ closeTeacherMenu(); }catch(e){}
    try{ closeAdminMenu(); }catch(e){}
    try{ closeHijriFloatingPanel(); }catch(e){}
    try{ closeVirtEditor(); }catch(e){}
    try{ closeVirtReader(); }catch(e){}

    /* Açık modal/overlay varsa görünmez yap; sayfa geçişinde arkada kalmasın. */
    try{
      document.querySelectorAll(
        '.modal:not(.hidden), .overlay:not(.hidden), [role="dialog"]:not(.hidden)'
      ).forEach(function(el){
        if(el.id==="