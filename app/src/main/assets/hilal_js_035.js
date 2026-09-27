
(function(){
'use strict';

class SwipeNavigationController{
  constructor(){
    this.routes=[];
    this.index=0;
    this.gesture=null;
    this.transitioning=false;
    this.bound=false;
    this.lastSwipeAt=0;
    this.threshold=Math.max(52,Math.min(76,Math.round((window.innerWidth||360)*0.15)));
    this.axisRatio=1.18;
    this.learnKey='hilal_swipe_v108_learned';
    this.successKey='hilal_swipe_v108_success';
    this.coachViewsKey='hilal_swipe_v108_coach_views';
  }

  role(){
    if(document.body.classList.contains('admin-mode'))return 'admin';
    if(document.body.classList.contains('teacher-mode'))return 'teacher';
    if(document.body.classList.contains('guest-mode'))return 'guest';
    const r=String((window.currentUser&&window.currentUser.role)||window.selectedRole||'guest').toLowerCase();
    if(r.includes('admin'))return 'admin';
    if(r.includes('teacher')||r.includes('hoca'))return 'teacher';
    return 'guest';
  }
  menu(){return document.getElementById(this.role()==='admin'?'adminMenu':this.role()==='teacher'?'teacherMenu':'guestMenu')}
  grid(){const m=this.menu();return m?.querySelector('.guest-menu-grid,.teacher-menu-grid,.admin-menu-grid')||m||null}
  appOpen(){const a=document.getElementById('mainApp');if(!a||a.classList.contains('hidden'))return false;const s=getComputedStyle(a);return s.display!=='none'&&s.visibility!=='hidden'}
  menuOpen(){return ['guestMenu','teacherMenu','adminMenu'].some(id=>{const e=document.getElementById(id);if(!e)return false;const s=getComputedStyle(e);return !e.classList.contains('hidden')&&s.display!=='none'&&s.visibility!=='hidden'})}
  modalOpen(){
    const els=Array.from(document.querySelectorAll('.modal,[role="dialog"],#hilalDuaComposerV55,#hilalDuaAddSheetV53,#hilalDuaComposeV54,#hilalDuaComposerV55'));
    return els.some(e=>{const s=getComputedStyle(e);const r=e.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&s.pointerEvents!=='none'&&r.width>2&&r.height>2&&(e.classList.contains('show')||e.classList.contains('open')||e.className.includes('hdv')||e.getAttribute('aria-hidden')==='false')});
  }
  blockedTarget(t){
    if(!t?.closest)return false;

    /* STABLE V4 — DUA İSTEKLERİ KAPALI KUTU
       Bu root içindeyken global yatay swipe/navigation motoru dokunma olayına girmez.
       Dua İstekleri yalnız kendi controller/motorlarını kullanır. */
    if(t.closest('#hilalDuaPageV2,[data-hilal-isolated-module="dua-istekleri"]')) return true;

    /* V120 FEEDBACK INPUT/TEXTAREA SWIPE FIX
       Görselde işaretlenen iki gerçek yazı alanı da artık swipe yüzeyidir:
       - #feedbackSubject
       - #feedbackMessage
       Dokunma ile yazı odağı korunur; yatay hareket başlatılırsa navigasyon çalışır. */
    if(t.closest('#feedbackCenterPage')){
      if(t.closest('#feedbackSubject,#feedbackMessage')) return false;
      return !!t.closest('select,[contenteditable="true"],[role="slider"]');
    }

    return !!t.closest(
      'button,a,[role="button"],summary,input,textarea,select,[contenteditable="true"],' +
      '[data-no-swipe="1"],[role="slider"],[data-hzv2-counter],[data-hhr3-juz],' +
      '[data-hhr3-open],[data-hzv2-open],[data-hzv2-take],[data-hzv2-minus],[data-hzv2-complete]'
    );
  }

  routeFromButton(btn){
    if(!btn)return null;
    const text=(btn.textContent||'').replace(/\s+/g,' ').trim();
    const low=text.toLocaleLowerCase('tr-TR');
    const direct=btn.dataset?.gpage||btn.dataset?.tpage||btn.dataset?.apage||btn.dataset?.page||'';
    const onclick=btn.getAttribute?.('onclick')||'';
    let key=direct;
    if(btn.id==='kazaNamazlarimMenuItem'||/kaza namaz/i.test(low))key='kazaNamazlarimPage';
    else if(btn.classList.contains('hilal-zikirmatik-menu-card-v13')||/zikirmatik/i.test(low))key='hilalZikirmatikPageV13';
    else if(btn.hasAttribute('data-hilal-dua-menu')||/dua istek/i.test(low)||/openHilalDuaRequests/i.test(onclick))key='hilalDuaPageV2';
    /* V120 FEEDBACK ROUTE FIX:
       Öneri / Şikayet artık action:... değil, diğer navigasyon sayfaları gibi
       doğrudan kendi gerçek sayfa kimliğiyle swipe rotasına girer. */
    else if(/openFeedbackCenter/i.test(onclick)||(low.includes('öneri')&&low.includes('şikayet')))key='feedbackCenterPage';
    else if(!key)key='action:'+text+':'+onclick;
    return {key,btn,label:text};
  }

  findButton(key,buttons){
    return buttons.find(b=>this.routeFromButton(b)?.key===key)||null;
  }

  reorderVisibleMenu(buttons){
    /* V121: Kullanıcının sürükleyerek kaydettiği sıra tek gerçek sıradır.
       Swipe motoru artık menüyü sabit/öncelikli bir sıraya ZORLAMAZ. */
    return buttons;
  }

  rebuildRoutes(){
    const g=this.grid();
    const buttons=g?Array.from(g.querySelectorAll('button,a,[role="button"]')).filter(el=>
      !el.disabled && !el.hidden && !el.classList.contains('hidden')
    ):[];

    /* V63: Dinamik eklenen ana sınıflar menü sırası taramasında kaybolmasın. */
    const specials=[
      document.querySelector('.hilal-zikirmatik-menu-card-v13'),
      document.querySelector('[data-hilal-dua-menu="1"]'),
      document.getElementById('kazaNamazlarimMenuItem')
    ].filter(Boolean);
    for(const b of specials){if(!buttons.includes(b))buttons.push(b)}

    const routes=[],seen=new Set();

    /* Menü DOM'unda hangi sıra varsa sağ/sol kaydırma da TAM o sırayı kullanır. */
    for(const b of buttons){
      const r=this.routeFromButton(b);
      if(!r || seen.has(r.key))continue;
      seen.add(r.key);
      routes.push(r);
    }

    /* Takvim kartı herhangi bir nedenle menü DOM'unda yoksa erişim kaybolmasın.
       Sadece eksikse güvenli geri dönüş rotası olarak başa eklenir. */
    if(!seen.has('calendarPage')){
      routes.unshift({key:'calendarPage',btn:null,label:'Takvim & Ezan'});
      seen.add('calendarPage');
    }

    this.routes=routes;
    const active=this.detectActiveKey();
    const i=routes.findIndex(r=>r.key===active);
    if(i>=0)this.index=i;
    else if(this.index>=routes.length)this.index=Math.max(0,routes.length-1);
    return routes;
  }

  detectActiveKey(){
    const z=document.getElementById('hilalZikirmatikPageV13');
    if(z&&(document.body.classList.contains('hilal-zikirmatik-open-v13')||z.getAttribute('aria-hidden')==='false'||z.classList.contains('is-open')))return 'hilalZikirmatikPageV13';
    const k=document.getElementById('kazaNamazlarimPage');if(k){const s=getComputedStyle(k);const r=k.getBoundingClientRect();if(s.display!=='none'&&s.visibility!=='hidden'&&r.width>2&&r.height>2)return 'kazaNamazlarimPage'}
    const dh=document.getElementById('hilalDuaV165Host');if(dh&&dh.classList.contains('is-open')&&dh.getAttribute('aria-hidden')==='false')return 'hilalDuaPageV2';
    const d=document.getElementById('hilalDuaPageV2');if(d&&(d.getAttribute('aria-hidden')==='false'||d.classList.contains('hdv51-open')||d.classList.contains('hdv2-open')))return 'hilalDuaPageV2';

    /* V120 FEEDBACK ROUTE FIX:
       Öneri / Şikayet artık gerçek feedbackCenterPage anahtarını kullandığı için
       özel action-route eşleştirmesine ihtiyaç yok. */
    const p=document.querySelector('#mainApp .page.active');
    return p?.id||'calendarPage';
  }

  closeSpecialExcept(key){
    if(key!=='hilalDuaPageV2'){
      try{window.closeHilalDuaV165Integrated?.()}catch(_){}
      try{window.HilalDuaScopeV97?.close?.()}catch(_){}
      try{window.closeHilalDuaRequestsV2?.()}catch(_){}
    }

    if(key!=='hilalZikirmatikPageV13'){
      try{window.closeHilalZikirmatikV13?.()}catch(_){}
      document.body.classList.remove('hilal-zikirmatik-open-v13','zikirmatik-page-open');
    }

    if(key!=='kazaNamazlarimPage'){
      try{window.closeKaza?.()}catch(_){}
      const k=document.getElementById('kazaNamazlarimPage');
      if(k)k.style.display='none';

      /* V120: Swipe ile Kaza'dan başka sayfaya/Takvim'e dönüldüğünde
         arka ekran kilidini kesin kaldır. */
      document.documentElement.classList.remove('kaza-page-open');
      document.body.classList.remove('kaza-page-open','hilal-kaza-active');

      document.body.style.position='';
      document.body.style.top='';
      document.body.style.left='';
      document.body.style.right='';
      document.body.style.width='';
      document.documentElement.style.overflow='';
      document.body.style.overflow='';
    }
  }

  openNormalPage(id,btn){
    this.closeSpecialExcept(id);
    try{
      if(this.role()==='guest'&&typeof window.guestGoPage==='function'){window.guestGoPage(id,btn||null);return true}
      if(this.role()==='teacher'&&typeof window.teacherGoPage==='function'){window.teacherGoPage(id,btn||null);return true}
      if(typeof window.showPage==='function'){window.showPage(id);return true}
    }catch(_){}
    return false;
  }

  activate(route){
    if(!route)return false;
    ['guestMenu','teacherMenu','adminMenu'].forEach(id=>document.getElementById(id)?.classList.add('hidden'));
    const k=route.key;
    if(k==='calendarPage'||k==='todayPage'||/Page$/.test(k)&&!['kazaNamazlarimPage','hilalDuaPageV2','hilalZikirmatikPageV13'].includes(k)){
      if(k==='calendarPage'){
        document.documentElement.classList.remove('kaza-page-open');
        document.body.classList.remove('kaza-page-open','hilal-kaza-active','hilal-zikirmatik-open-v13','zikirmatik-page-open');
        ['position','top','left','right','width','overflow'].forEach(p=>document.body.style[p]='');
        document.documentElement.style.overflow='';
      }
      if(document.getElementById(k))return this.openNormalPage(k,route.btn);
    }
    if(k==='hilalDuaPageV2'){this.closeSpecialExcept(k);try{if(typeof window.openHilalDuaV165Integrated==='function'){window.openHilalDuaV165Integrated();return true}window.openHilalDuaRequestsV2?.();return true}catch(_){}}
    if(k==='hilalZikirmatikPageV13'){this.closeSpecialExcept(k);try{window.openHilalZikirmatikV13?.();return true}catch(_){}}
    if(k==='kazaNamazlarimPage'){this.closeSpecialExcept(k);try{route.btn?.click();return true}catch(_){}}
    if(k==='feedbackCenterPage'){
      this.closeSpecialExcept(k);
      try{
        if(typeof window.openFeedbackCenter==='function'){
          window.openFeedbackCenter();
          return true;
        }
        return this.openNormalPage(k,route.btn);
      }catch(_){
        return this.openNormalPage(k,route.btn);
      }
    }
    try{route.btn?.click();return true}catch(_){return false}
  }

  curtain(){let c=document.getElementById('hilalSwipeCurtainV108');if(!c){c=document.createElement('div');c.id='hilalSwipeCurtainV108';document.body.appendChild(c)}return c}
  activeRoot(){const key=this.detectActiveKey();return document.getElementById(key)||document.querySelector('#mainApp .page.active')}

  async transitionTo(route){
    /* V119: kararma/bekleme yok; sayfa doğrudan açılır. */
    const ok=this.activate(route);

    /* Takvim & Ezan'a swipe ile geri dönüldüğünde dikey scroll motorunu
       aynı karede yeniden bağla. Başka sınıflara müdahale etmez. */
    if(ok && route?.key==='calendarPage'){
      try{
        const c=window.VerticalPageScrollControllerV112;
        c?.apply?.();
        requestAnimationFrame(()=>{
          try{
            c?.apply?.();
            const cal=document.getElementById('calendarPage');
            cal?.classList.add('hilal-vertical-scroll-v112');
          }catch(_){}
        });
      }catch(_){}
    }
    return !!ok;
  }

  async go(delta){
    if(this.transitioning)return false;
    this.rebuildRoutes();
    const detected=this.detectActiveKey();const di=this.routes.findIndex(r=>r.key===detected);if(di>=0)this.index=di;
    const next=this.index+delta;
    if(next<0||next>=this.routes.length)return false;
    const now=performance.now();if(now-this.lastSwipeAt<170)return false;this.lastSwipeAt=now;
    this.transitioning=true;
    try{const ok=await this.transitionTo(this.routes[next]);if(ok){this.index=next;this.recordSuccess();return true}return false}
    finally{setTimeout(()=>{this.transitioning=false},40)}
  }

  start(e){
    if(this.transitioning||!this.appOpen()||this.menuOpen()||this.modalOpen()||e.touches?.length!==1||this.blockedTarget(e.target)){this.gesture=null;return}
    const p=e.touches[0];this.gesture={x:p.clientX,y:p.clientY,axis:null,fired:false};
  }
  move(e){
    const g=this.gesture;if(!g||g.fired||this.transitioning||!e.touches?.length)return;
    const p=e.touches[0],dx=p.clientX-g.x,dy=p.clientY-g.y,ax=Math.abs(dx),ay=Math.abs(dy);
    if(g.axis===null&&Math.hypot(dx,dy)>10){if(ax>ay*this.axisRatio)g.axis='x';else if(ay>ax)g.axis='y'}
    if(g.axis==='y'){this.gesture=null;return}
    if(g.axis!=='x')return;
    if(e.cancelable)e.preventDefault();
    if(ax>=this.threshold){g.fired=true;this.go(dx<0?1:-1)}
  }
  end(){this.gesture=null}

  syncFromMenuClick(e){
    const b=e.target?.closest?.('button,a,[role="button"]');if(!b)return;const g=this.grid();if(!g||!g.contains(b))return;
    setTimeout(()=>{this.rebuildRoutes();const r=this.routeFromButton(b);const i=this.routes.findIndex(x=>x.key===r?.key);if(i>=0)this.index=i},60);
  }

  bindFrame(frame){
    if(!frame||frame.dataset.hilalSwipeV108==='1')return;
    const attach=()=>{try{const d=frame.contentDocument;if(!d)return;d.addEventListener('touchstart',e=>this.start(e),{capture:true,passive:true});d.addEventListener('touchmove',e=>this.move(e),{capture:true,passive:false});d.addEventListener('touchend',()=>this.end(),{capture:true,passive:true});d.addEventListener('touchcancel',()=>this.end(),{capture:true,passive:true});frame.dataset.hilalSwipeV108='1'}catch(_){}};
    frame.addEventListener('load',attach);setTimeout(attach,150);
  }
  bindFrames(){document.querySelectorAll('#hilalZikirmatikPageV13 iframe,#kazaNamazlarimPage iframe').forEach(f=>this.bindFrame(f))}

  coach(){let c=document.getElementById('hilalSwipeCoachV108');if(c)return c;c=document.createElement('div');c.id='hilalSwipeCoachV108';c.innerHTML='<div class="hand">☝️</div><div><strong>Sağa / sola kaydırın</strong><span>Takvim & Ezan’dan başlayarak sayfalar sırayla değişir</span></div>';document.body.appendChild(c);return c}
  showCoach(){if(localStorage.getItem(this.learnKey)==='1'||!this.appOpen()||this.detectActiveKey()!=='calendarPage')return;const n=Number(localStorage.getItem(this.coachViewsKey)||0);if(n>=4)return;localStorage.setItem(this.coachViewsKey,String(n+1));const c=this.coach();c.classList.remove('show');void c.offsetWidth;c.classList.add('show');setTimeout(()=>c.classList.remove('show'),5100)}
  recordSuccess(){const n=Number(localStorage.getItem(this.successKey)||0)+1;localStorage.setItem(this.successKey,String(n));if(n>=3){localStorage.setItem(this.learnKey,'1');document.getElementById('hilalSwipeCoachV108')?.classList.remove('show')}}

  init(){
    if(this.bound)return;this.bound=true;
    this.rebuildRoutes();
    document.addEventListener('touchstart',e=>this.start(e),{capture:true,passive:true});
    document.addEventListener('touchmove',e=>this.move(e),{capture:true,passive:false});
    document.addEventListener('touchend',()=>this.end(),{capture:true,passive:true});
    document.addEventListener('touchcancel',()=>this.end(),{capture:true,passive:true});
    document.addEventListener('click',e=>this.syncFromMenuClick(e),true);
    document.addEventListener('hilal:navigation-sync',()=>{this.rebuildRoutes();this.bindFrames()});
    this.bindFrames();
    setTimeout(()=>{this.rebuildRoutes();this.bindFrames();this.showCoach()},900);
    setTimeout(()=>this.showCoach(),1900);
  }
}

window.SwipeNavigationController=new SwipeNavigationController();
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',()=>window.SwipeNavigationController.init(),{once:true});else window.SwipeNavigationController.init();
})();
</script>
<script id="hilalIOSWheelPickerV109">
/* ===== V109 — TEK SINIFLI PREMIUM WHEEL PICKER ALTYAPISI ===== */
(function(){
  const ITEM_H=41;
  const pad2=n=>String(n).padStart(2,'0');
  const normTime=v=>{const p=String(v||'09:00:00').split(':');return `${pad2(Number(p[0]||0))}:${pad2(Number(p[1]||0))}:${pad2(Number(p[2]||0))}`};

  class IOSWheelColumn{
    constructor(role,values,labels,onChange){this.role=role;this.values=values;this.labels=labels||values;this.onChange=onChange;this.value=values[0];this.timer=0;this.programmatic=false;this.el=document.createElement('div');this.el.className='hilal-ios-col';this.el.dataset.role=role;this.render();this.bind()}
    render(){this.el.innerHTML=this.values.map((v,i)=>`<div class="hilal-ios-item" data-i="${i}" data-v="${String(v).replace(/"/g,'&quot;')}">${this.labels[i]}</div>`).join('')}
    bind(){this.el.addEventListener('scroll',()=>{clearTimeout(this.timer);this.timer=setTimeout(()=>this.co