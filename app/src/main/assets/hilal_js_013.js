
(function(){
"use strict";

const PERM_KEY="hilal_teacher_admin_permissions_v2";
const CLOUD_BASE=HILAL_FIREBASE_DATABASE_URL+"/shared/hilal_teacher_permissions_v2";
let selectedPid="";

const DEFS=[
 {id:"teacher_approval",icon:"✅",name:"Hoca Onay İşlemleri",note:"Onay bekleyen hocaları görüntüleme ve onaylama.",open:"openAdminMain"},
 {id:"announcements",icon:"📣",name:"Duyuru Yönetimi",note:"Duyuruları görüntüleme ve yönetme.",open:"openAdminModerationPage"},
 {id:"publications",icon:"📚",name:"Yayınlar Yönetimi",note:"Yayınları görüntüleme ve yönetme.",open:"openAdminPublicationsPage"},
 {id:"hijri_daily",icon:"🌙",name:"Hicrî Günlük İçerikler",note:"Hicrî günlük içerikleri yönetme.",open:"openHijriDailyAdminPage"},
 {id:"approved_teacher