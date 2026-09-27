
class StandardPageModule extends HilalModuleBase {
  constructor(id,title,rootId){ super({id,title,rootId}); }
  open(){
    if(typeof window.showPage==='function') window.showPage(this.rootId);
    else { document.querySelectorAll('#mainApp .page').forEach(p=>p.classList.add('hidden')); this.root()?.classList.remove('hidden'); }
    this.syncNavigation();
  }
}
const HILAL_STANDARD_PAGES=[
 ['bugun','Bugün','todayPage'],['gunluk-vird','Günlük Vird','dailyVirtPage'],['sunnet-ihya','Sünnet / İhya','sunnetIhyaPage'],
 ['duyurular','Duyurular','announcementsPage'],['genel-yayinlar','Genel Yayınlar','generalPublicationsPage'],['favoriler','Favoriler','favoritesPage'],
 ['ayarlar','Ayarlar','settingsPage'],['geri-bildirim','Geri Bildirim','feedbackCenterPage'],['yonetici','Yönetici','adminPage'],
 ['hoca-takipci