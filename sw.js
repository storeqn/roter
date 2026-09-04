const CACHE='yasri-pwa-v17';
self.addEventListener('install',event=>{self.skipWaiting();});
self.addEventListener('activate',event=>{
  event.waitUntil((async()=>{
    const keys=await caches.keys();
    await Promise.all(keys.filter(k=>k!==CACHE).map(k=>caches.delete(k)));
    await self.clients.claim();
  })());
});

async function enhanceAdminResponse(request,response){
  try{
    const url=new URL(request.url);
    if(url.origin!==self.location.origin||!url.pathname.endsWith('/admin.html')) return response;
    if(!response||!response.ok) return response;
    let html=await response.text();

    html=html.replace(
      'function scooterIcon(active,late=false){return L.divIcon({className:"scooter-icon-wrap",html:`<div class="scooter-pin ${active?"on":"off"} ${late?"late":""}"><span>🛵</span></div>`,iconSize:[52,52],iconAnchor:[26,26]})}',
      'function scooterIcon(active,late=false,returning=false){return L.divIcon({className:"scooter-icon-wrap",html:`<div class="scooter-pin ${active?"on":"off"} ${late?"late":""}" style="${returning?"background:#b45309!important;border-color:#f59e0b!important;box-shadow:0 0 0 7px rgba(245,158,11,.18),0 5px 16px rgba(0,0,0,.45)":""}"><span>🛵</span>${returning?`<span style="position:absolute;right:-18px;top:-15px;background:#f59e0b;color:#111827;font-size:11px;font-weight:900;padding:4px 7px;border-radius:999px;white-space:nowrap;border:2px solid #fff">↩ عائد</span>`:""}</div>`,iconSize:[52,52],iconAnchor:[26,26]})}'
    );

    html=html.replaceAll('scooterIcon(active,late)', 'scooterIcon(active,late,driverState==="returning")');
    html=html.replace(
      'markers[id].bindTooltip(esc(d.name||"دلفري"),{permanent:true,direction:"top",className:"driver-name-label",offset:[0,-23]});',
      'markers[id].bindTooltip(esc((d.name||"دلفري")+(driverState==="returning"?" • عائد للمطعم":"")),{permanent:true,direction:"top",className:"driver-name-label",offset:[0,-23]});'
    );
    html=html.replace(
      'الحالة: ${orderStatusLabel(d.orderStatus)}<br>${active?"🟢 في الدوام":"⚪ خارج الدوام"}',
      'الحالة: ${stateText}<br>${active?"🟢 في الدوام":"⚪ خارج الدوام"}'
    );

    const headers=new Headers(response.headers);
    headers.set('content-type','text/html; charset=utf-8');
    headers.delete('content-length');
    return new Response(html,{status:response.status,statusText:response.statusText,headers});
  }catch(e){
    return response;
  }
}

self.addEventListener('fetch',event=>{
  if(event.request.method!=='GET') return;
  event.respondWith((async()=>{
    try{
      const response=await fetch(event.request);
      return await enhanceAdminResponse(event.request,response);
    }catch(e){
      const cached=await caches.match(event.request);
      return cached||Response.error();
    }
  })());
});