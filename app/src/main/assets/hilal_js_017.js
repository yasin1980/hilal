
(function(){
"use strict";

const MONTHS=["Muharrem","Safer","Rebiülevvel","Rebiülahir","Cemaziyelevvel","Cemaziyelahir","Recep","Şaban","Ramazan","Şevval","Zilkade","Zilhicce"];
const DAILY_KEY="hilal_hijri_daily_content_v1";

function myPerms(){
  try{
    const x=getCurrentTeacherPermissions();
    return Array.isArray(x)?x:[];
  }catch(e){return[]}
}
function canHijri(){return currentUser?.role==="teacher" && myPerms().includes("hijri_daily")}
function entries(){
  try{return loadJSON(DAILY_KEY,[])||[]}catch(e){return[]}
}
function saveEntries(arr){
  try{saveJSON(DAILY_KEY,arr)}catch(e){localStorage.setItem(DAILY_KEY,JSON.stringify(arr))}
  try{if(typeof refreshHijriDailyEverywhere==="function")refreshHijriDailyEverywhere()}catch(e){}
}
function e