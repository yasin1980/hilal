
class TakvimEzanModule extends HilalModuleBase {
  constructor(){ super({id:'takvim-ezan',title:'Takvim & Ezan',rootId:'calendarPage'}); }
  open(){
    try{
      if(typeof window.showPage==='function') window.showPage('calendarPage');
      else {
        document.querySelectorAll('#mainApp .page').forEach(p=>p.classList.add('hidden'));
        this.root()?.classList.remove('hidden');
      }
    } finally { this.syncNavigation(); }
  }
}
window.hilalModules.register(new TakvimEzanModule());
window.TakvimEzanModule=TakvimEzanModule;

</script>

<script data-inline-source="packages/kaza-namazlari/KazaNamazlariModule.js">
class KazaNamazlariModule extends HilalModuleBase {
  constructor(){ super({id:'kaza-namazlari',title:'Kaza Namazlarım',rootId:'kazaNamazlarimPage',type:'overlay'}); }
  activateNavigat