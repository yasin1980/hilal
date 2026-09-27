
(function(){
"use strict";
const KEY="hilal_hijri_daily_content_v1";
const MONTHS=["Muharrem","Safer","Rebiülevvel","Rebiülahir","Cemaziyelevvel","Cemaziyelahir","Recep","Şaban","Ramazan","Şevval","Zilkade","Zilhicce"];
const TYPES=["Günün İbadeti","Günün Zikri","Günün Duası","Günün Salavatı"];

function esc(v){return String(v??"").replace(/[&<>"']/g,m=>({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"}[m]))}
function load(){try{return loadJSON(KEY,[])||[]}catch(e){return[]}}
function save(a){try{saveJSON(KEY,a)}catch(e){localStorage.setItem(KEY,JSON.stringify(a))}try{refreshHijriDailyEverywhere()}catch(e){}}
function uid(){return "d_"+Date.now()+"_"+Math.random().toString(36).slice(2,8)}
function allowed(){try{return currentUser?.role==="teacher" &&