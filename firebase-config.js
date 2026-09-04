export const firebaseConfig = {
  apiKey: "AIzaSyD121IwvJ1RXq-WsYof6mDCwJxco1IBDy8",
  authDomain: "delivery-tracker-febcc.firebaseapp.com",
  databaseURL: "https://delivery-tracker-febcc-default-rtdb.europe-west1.firebasedatabase.app",
  projectId: "delivery-tracker-febcc",
  storageBucket: "delivery-tracker-febcc.firebasestorage.app",
  messagingSenderId: "1038366061508",
  appId: "1:1038366061508:web:7d34a4ada107c3a13b2a91"
};

export const firebaseReady = !Object.values(firebaseConfig).some(v => String(v).includes("PUT_"));

/* Admin completed-history UI enhancement */
if (typeof window !== "undefined" && /admin\.html(?:$|[?#])/.test(location.href)) {
  const run = () => {
    const panel = document.querySelector(".history-panel");
    const list = document.getElementById("historyList");
    if (!panel || !list || panel.dataset.historyEnhanced === "1") return;
    panel.dataset.historyEnhanced = "1";

    const style = document.createElement("style");
    style.textContent = `
      .history-panel{padding:0!important;overflow:hidden}
      .history-master-head{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:17px 18px;cursor:pointer;user-select:none}
      .history-master-head h2{margin:0;font-size:22px}
      .history-master-meta{display:flex;align-items:center;gap:10px;color:#94a3b8;font-size:13px}
      .history-master-arrow,.history-day-arrow{width:30px;height:30px;border-radius:9px;display:grid;place-items:center;background:#13233a;color:#60a5fa;font-weight:900;transition:transform .2s ease}
      .history-master-head.open .history-master-arrow,.history-day.open>.history-day-head .history-day-arrow{transform:rotate(180deg)}
      .history-days-wrap{padding:0 14px 14px;display:grid;gap:9px}
      .history-day{border:1px solid #26364f;border-radius:15px;overflow:hidden;background:#0a1728}
      .history-day.open{border-color:#1685ff;box-shadow:0 0 0 1px rgba(22,133,255,.12) inset}
      .history-day-head{display:grid;grid-template-columns:minmax(0,1fr) auto auto;align-items:center;gap:10px;padding:13px 14px;cursor:pointer;background:#0c1b2e}
      .history-day.open>.history-day-head{background:linear-gradient(90deg,rgba(13,69,139,.45),rgba(10,27,46,.95))}
      .history-day-date{font-weight:850;font-size:15px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
      .history-day-count{padding:5px 10px;border-radius:999px;background:#17345a;color:#dbeafe;font-size:12px;font-weight:800}
      .history-day-body{display:none;padding:9px;gap:7px}
      .history-day.open>.history-day-body{display:grid}
      .history-day-body .history-item{margin:0;border-radius:12px;background:#0b1728}
      .history-empty{padding:18px;color:#94a3b8;text-align:center}
      @media(max-width:800px){
        .history-master-head{padding:14px}.history-master-head h2{font-size:19px}
        .history-days-wrap{padding:0 9px 10px}.history-day-head{padding:12px 10px;gap:7px}
        .history-day-date{font-size:14px}.history-day-count{font-size:11px;padding:4px 8px}
        .history-day-body .history-item{grid-template-columns:1fr 1fr!important;padding:10px}
      }
    `;
    document.head.appendChild(style);

    const oldLine = panel.querySelector(".section-line");
    if (oldLine) oldLine.remove();

    const master = document.createElement("div");
    master.className = "history-master-head";
    master.innerHTML = `<div><h2>📋 سجل الطلبات المكتملة</h2><div class="muted small" style="margin-top:4px">اضغط لعرض السجل حسب الأيام</div></div><div class="history-master-meta"><span id="historyMasterCount">0 طلب</span><span class="history-master-arrow">⌄</span></div>`;
    panel.insertBefore(master, list);

    const wrap = document.createElement("div");
    wrap.className = "history-days-wrap";
    wrap.hidden = true;
    panel.insertBefore(wrap, list);
    list.style.display = "none";

    master.addEventListener("click", () => {
      const open = wrap.hidden;
      wrap.hidden = !open;
      master.classList.toggle("open", open);
    });

    const arabicToLatin = s => String(s || "").replace(/[٠-٩]/g, d => "٠١٢٣٤٥٦٧٨٩".indexOf(d)).replace(/[۰-۹]/g, d => "۰۱۲۳۴۵۶۷۸۹".indexOf(d));
    const getDateLabel = item => {
      const blocks = item.querySelectorAll(":scope > div");
      const last = blocks[blocks.length - 1];
      const b = last && last.querySelector("b");
      const raw = (b ? b.textContent : item.textContent).trim();
      const latin = arabicToLatin(raw).replace(/[\u200e\u200f\u061c]/g, "");
      const m = latin.match(/(\d{1,4})\s*[\/\-]\s*(\d{1,2})\s*[\/\-]\s*(\d{1,4})/);
      if (!m) return {key:"unknown", label:"تاريخ غير محدد", ts:0};
      let a=+m[1], mo=+m[2], c=+m[3], y,d;
      if (a > 31) { y=a; d=c; } else { d=a; y=c; }
      if (y < 100) y += 2000;
      const dt = new Date(y, mo-1, d);
      const key = `${y}-${String(mo).padStart(2,"0")}-${String(d).padStart(2,"0")}`;
      const today = new Date(); today.setHours(0,0,0,0);
      const yesterday = new Date(today); yesterday.setDate(today.getDate()-1);
      let prefix = "";
      if (+dt === +today) prefix = "اليوم - "; else if (+dt === +yesterday) prefix = "أمس - ";
      return {key,label:prefix+dt.toLocaleDateString("ar-IQ",{weekday:"long",year:"numeric",month:"numeric",day:"numeric"}),ts:+dt};
    };

    let busy = false;
    const rebuild = () => {
      if (busy) return;
      busy = true;
      const items = [...list.children].filter(el => el.classList.contains("history-item"));
      const countEl = document.getElementById("historyMasterCount");
      if (countEl) countEl.textContent = items.length + " طلب";
      wrap.innerHTML = "";
      if (!items.length) {
        const msg = document.createElement("div"); msg.className="history-empty"; msg.textContent="لا توجد طلبات مكتملة حتى الآن."; wrap.appendChild(msg); busy=false; return;
      }
      const groups = new Map();
      items.forEach(item => {
        const info=getDateLabel(item);
        if(!groups.has(info.key)) groups.set(info.key,{...info,items:[]});
        groups.get(info.key).items.push(item.cloneNode(true));
      });
      [...groups.values()].sort((x,y)=>y.ts-x.ts).forEach((g,index)=>{
        const day=document.createElement("section"); day.className="history-day";
        const head=document.createElement("div"); head.className="history-day-head";
        head.innerHTML=`<div class="history-day-date">📅 ${g.label}</div><span class="history-day-count">${g.items.length} ${g.items.length===1?"طلب":"طلبات"}</span><span class="history-day-arrow">⌄</span>`;
        const body=document.createElement("div"); body.className="history-day-body";
        g.items.forEach(x=>body.appendChild(x));
        head.onclick=()=>day.classList.toggle("open");
        day.append(head,body); wrap.appendChild(day);
      });
      busy=false;
    };

    const observer = new MutationObserver(() => setTimeout(rebuild, 0));
    observer.observe(list,{childList:true});
    rebuild();
  };
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded",()=>setTimeout(run,0)); else setTimeout(run,0);
}