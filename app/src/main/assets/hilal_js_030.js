
class ZikirmatikModule extends HilalModuleBase {
  constructor(){ super({id:'zikirmatik',title:'Zikirmatik',rootId:'hilalZikirmatikPageV13',type:'overlay'}); }
  activateNavigation(){
    document.body.classList.add('hilal-zikirmatik-open-v13');
    try{ window.hilalNavigation?.init?.(); }catch(_){ }
    try{ window.hilalNavigation?.sync?.(); }catch(_){ }
    document.dispatchEvent(new CustomEvent('hilal:navigation-sync'));
  }
  deactivateNavigation(){
    document.body.classList.remove('hilal-zikirmatik-open-v13');
    try{ window.hilalNavigation?.sync?.(); }catch(_){ }
  }
  open(){
    if(typeof window.openZikirmatik==='function') window.openZikirmatik();
    else { const p=this.root(); if(p){p.classList.add('is-open');p.setAttribute('aria-hidden','false');} }
    const p=this.root(); if(p){p.classLis