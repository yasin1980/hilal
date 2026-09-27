
(function(){
"use strict";

function getMyPermsV1414(){
  try{
    if(typeof getCurrentTeacherPermissions==="function"){
      const x=getCurrentTeacherPermissions();
      return Array.isArray(x)?x:[];
    }
  }catch(e){}
  return [];
}

function ensureCardV1414(){
  if(document.getElementById("teacherSettingsDelegatedCard"))return;

  const settings=document.getElementById("settingsPage");
  if(!settings)return;

  const stack=settings.querySelector(".settings-stack") || settings.querySelector(".content-stack") || settings;
  const card=document.createElement("article");
  card.id="teacherSettingsDelegatedCard";
  card.className="account-settings-card account-profile-card profile-settings-shell";
  card.innerHTML=`
    <button type="butto