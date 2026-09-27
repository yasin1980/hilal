
(function(){
  let lastHomeBackAt = 0;
  const EXIT_WINDOW = 2200;

  function isVisible(el){
    if(!el) return false;
    const s = window.getComputedStyle(el);
    return s.display !== "none" && s.visibility !== "hidden" && !el.classList.contains("hidden");
  }

  function findHome(){
    return document.querySelector(
      '#homePage, #home-page, #mainPage, #main-page, [data-page="home"], .home-page'
    );
  }

  function onHome(){
    const home = findHome();
    if(home) return isVisible(home);

    // Mevcut uygulamanın sayfa yapısında görünür ana ekran dışında açık bir page varsa alt sayfadayız.
    const pages = Array.from(document.querySelectorAll('.page'));
    const visible = pages.filter(isVisible);
    if(!visible.length) return true;
    return visible.some(p => /home|main|ana/