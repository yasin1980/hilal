
(function(){
"use strict";

/* TEK ID STANDARDI: YYAAGG + 4 haneli sıra. Eski ID sistemleri kullanılmaz. */
const IDKEY="hilal_teacher_final_id_v1409";
const COUNTERKEY="hilal_teacher_daily_counter_v1409";
const LEGACY_KEYS=[
 "hilal_teacher_permanent_device_id_v1",
 "hilal_teacher_date_sequence_id_v2",
 "hilal_teacher_final_id_v1408",
 "hilal_teacher_public_id_map_v1"
];

function p2(n){return String(n).padStart(2,"0")}
function dayPrefix(d){
 d=d||new Date();
 return p2(d.getFullYear()%100)+p2(d.getMonth()+1)+p2(d.getDate());
}
function valid(v){return /^\d{10}$/.test(String(v||"").replace(/^HILAL-/i,"").trim())}
function readId(){try{return String(localStorage.getItem(IDKEY)||"").trim()}catch(e){return ""}}
function counters(){try{return