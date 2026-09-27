
(function(){
"use strict";

let delegatedTeacherContext=false;

function teacherHomeV1415(){
  delegatedTeacherContext=false;
  try{document.body.classList.remove("delegated-teacher-context")}catch(e){}
  try{teacherGoPage("todayPage",null);return}catch(e){}
  try{showPage("todayPage")}catch(e){}
}
window.teacherDelegatedGoHomeV1415=teacherHomeV1415;

function prepareDelegatedPageV1415(){
  const page=document.getElementById("teacherDelegatedAdminPage");
  if(!page)return;

  const back=page.querySelector(".admin-back");
  if(back){
    back.textContent="← Hoca Ana Sayfa";
    back.setAttribute("onclick","teacherDelegatedGoHomeV1415()");
  }

  const hero=page.querySelector(".hero");
  if(hero){
    const kicker=hero.querySelector(".muted");
  