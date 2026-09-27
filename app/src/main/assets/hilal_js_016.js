
(function(){
"use strict";

/* Hocanın yetki ekranından veya yetkili yönetim ekranından geri dönüşü
   doğrudan Takvim ve Namaz Vakitleri sayfasına gider. */
window.teacherDelegatedGoHomeV1415=function(){
  try{document.body.classList.remove("delegated-teacher-context")}catch(e){}
  try{showPage("calendarPage");return}catch(e){}
  try{teacherGoPage("calendarPage",null)}catch(e){}
};

function polishDelegatedPageV1416(){
  const page=document.getElementById("teacherDelegatedAdminPage");
  if(!page)return;

  const back=page.querySelector(".admin-back");
  if(back){
    back.textContent="← Takvim ve Namaz Vakitleri";
    back.setAttribute("onclick","teacherDelegatedGoHomeV1415()");
  }

  const hero=page.querySelector(".hero");
  if(hero){
    const kicker