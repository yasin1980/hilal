window.HILAL_VERSION="1.13.60D";</script>
<script id="hilalInlineDailyComposerScript">
/* V1.13.54 - day-local composer + optional image. Later declarations intentionally override legacy daily editor behavior. */
function inlineDailyKey(month,day){
  const mi=Math.max(0,HIJRI_MONTHS.indexOf(month));
  return `m${mi+1}d${Number(day)}`;
}
function inlineDailyFormHtml(month,day){
  const k=inlineDailyKey(month,day);
  const types=["Günün Duası","Günün Zikri","Günün İbadeti","Günün Salavatı"];
  return `<details class="hijri-inline-composer" id="inlineComposer_${k}" style="display:none">
    <summary><span class="hijri-inline-composer-title">＋ İçerik Ekle <small>${day} ${esc(month)}</small></span></summary>
    <div class="hijri-inline-form">
     