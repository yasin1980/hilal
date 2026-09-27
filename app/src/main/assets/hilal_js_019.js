
(function(){
"use strict";

function cleanLiteralTailV1424(){
  try{
    const walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);
    const remove=[];
    while(walker.nextNode()){
      const n=walker.currentNode;
      const t=n.nodeValue||"";
      if(/^\s*\\n(?:\\n|\s)*$/.test(t))remove.push(n);
    }
    remove.forEach(n=>n.parentNode&&n.parentNode.removeChild(n));
  }catch(e){}
}

function normalizeActivePageV1424(){
  try{
    document.querySelectorAll(".page").forEach(p=>{
      p.style.maxHeight="";
      if(p.classList.contains("active")){
        p.style.height="auto";
        p.style.minHeight="100dvh";
      }
    });
  }catch(e){}
}

document.addEventListener("DOMContentLoaded",()=>{
  cleanLiteralTailV1424();
  normalize