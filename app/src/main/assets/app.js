(() => {
'use strict';

const KEY='dividaEmDiaDataV2';
const $=s=>document.querySelector(s); const $$=s=>[...document.querySelectorAll(s)];
const uuid=()=>((window.crypto&&typeof window.crypto.randomUUID==='function')?window.crypto.randomUUID():('id-'+Date.now()+'-'+Math.random().toString(16).slice(2)));
const todayISO=()=>{const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`};
const parseDate=iso=>{const [y,m,d]=String(iso).split('-').map(Number);return new Date(y,m-1,d,12,0,0)};
const isoDate=d=>`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
const money=v=>new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'}).format(Number(v||0));
const dateBR=s=>parseDate(s).toLocaleDateString('pt-BR');
const dateLong=s=>parseDate(s).toLocaleDateString('pt-BR',{weekday:'long',day:'2-digit',month:'long',year:'numeric'});
const monthName=d=>d.toLocaleDateString('pt-BR',{month:'long',year:'numeric'}).replace(/^./,c=>c.toUpperCase());
const dayDiff=s=>Math.round((parseDate(s)-parseDate(todayISO()))/86400000);
const addMonths=(iso,n)=>{const d=parseDate(iso),day=d.getDate();d.setDate(1);d.setMonth(d.getMonth()+n);const last=new Date(d.getFullYear(),d.getMonth()+1,0).getDate();d.setDate(Math.min(day,last));return isoDate(d)};
const sameMonth=(iso,d=new Date())=>{const x=parseDate(iso);return x.getMonth()===d.getMonth()&&x.getFullYear()===d.getFullYear()};
const esc=s=>String(s??'').replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));

const iconMap={
  'Cartão de Crédito':'💳','Contas de Casa':'🏠','Internet':'📶','Água':'💧','Energia':'⚡','Telefone':'☎','Supermercado':'🛒','Saúde':'❤','Educação':'🎓','Transporte':'🚗','Empréstimo':'💰','Financiamento':'🚘','Assinaturas':'▶','Outros':'📌'
};
const iconFor=c=>iconMap[c]||'📌';

function sampleData(){
  const now=new Date(); const rel=n=>{const d=new Date(now.getFullYear(),now.getMonth(),now.getDate()+n,12);return isoDate(d)};
  const series1=uuid(),series2=uuid(),series3=uuid();
  return {
    version:2,
    settings:{userName:'José',darkMode:false,sound:true,reminders:[7,3,1,0]},
    categories:['Cartão de Crédito','Contas de Casa','Internet','Água','Energia','Telefone','Supermercado','Saúde','Educação','Transporte','Empréstimo','Financiamento','Assinaturas','Outros'],
    debts:[
      {id:uuid(),seriesId:series1,name:'Energia',amount:180,due:rel(0),category:'Energia',status:'pending',note:'Conta residencial',installment:1,totalInstallments:1,recurring:true,paidAt:null},
      {id:uuid(),seriesId:series2,name:'Cartão Nubank',amount:720,due:rel(2),category:'Cartão de Crédito',status:'pending',note:'Fatura do mês',installment:3,totalInstallments:12,recurring:false,paidAt:null},
      {id:uuid(),seriesId:series2,name:'Cartão Nubank',amount:720,due:addMonths(rel(2),1),category:'Cartão de Crédito',status:'pending',note:'Fatura do mês',installment:4,totalInstallments:12,recurring:false,paidAt:null},
      {id:uuid(),seriesId:series2,name:'Cartão Nubank',amount:720,due:addMonths(rel(2),2),category:'Cartão de Crédito',status:'pending',note:'Fatura do mês',installment:5,totalInstallments:12,recurring:false,paidAt:null},
      {id:uuid(),seriesId:series3,name:'Internet',amount:120,due:rel(8),category:'Internet',status:'pending',note:'Vivo Fibra',installment:1,totalInstallments:1,recurring:true,paidAt:null},
      {id:uuid(),seriesId:uuid(),name:'Academia',amount:80,due:rel(-4),category:'Saúde',status:'paid',note:'Mensal',installment:1,totalInstallments:1,recurring:true,paidAt:rel(-4)},
      {id:uuid(),seriesId:uuid(),name:'Supermercado',amount:350,due:rel(-8),category:'Supermercado',status:'paid',note:'Compra do mês',installment:1,totalInstallments:1,recurring:false,paidAt:rel(-8)}
    ]
  };
}

function normalize(raw){
  const fallback=sampleData(); if(!raw||typeof raw!=='object') return fallback;
  return {
    version:2,
    settings:{...fallback.settings,...(raw.settings||{}),reminders:Array.isArray(raw.settings?.reminders)?raw.settings.reminders:(raw.reminders||fallback.settings.reminders)},
    categories:Array.isArray(raw.categories)&&raw.categories.length?raw.categories:fallback.categories,
    debts:Array.isArray(raw.debts)?raw.debts.map(d=>({seriesId:d.seriesId||d.id||uuid(),installment:1,totalInstallments:1,recurring:false,paidAt:null,status:'pending',...d})):fallback.debts
  };
}

let data=normalize(JSON.parse(localStorage.getItem(KEY)||'null'));
let currentView='home', previousViews=[], debtFilter='all', historyFilter='paid', historyNewest=true;
let calDate=new Date(), selectedCalDay=todayISO(), detailId=null;

function save(sync=true){localStorage.setItem(KEY,JSON.stringify(data));if(sync)syncNativeReminders();}
function statusOf(d){if(d.status==='paid')return'paid';const n=dayDiff(d.due);if(n<0)return'late';if(n===0)return'today';return'pending';}
function statusLabel(s){return({paid:'Pago',late:'Atrasado',today:'Vence hoje',pending:'Pendente'})[s]||s;}
function dueText(d){const n=dayDiff(d.due);if(d.status==='paid')return`Pago em ${dateBR(d.paidAt||d.due)}`;if(n<0)return `${Math.abs(n)} ${Math.abs(n)===1?'dia':'dias'} atrasada`;if(n===0)return'Vence hoje';if(n===1)return'Vence amanhã';return`Vence em ${n} dias`;}
function monthDebts(){const now=new Date();return data.debts.filter(d=>sameMonth(d.due,now));}
function pendingDebts(){return data.debts.filter(d=>d.status!=='paid');}

function card(d){
 const s=statusOf(d); return `<button class="debt-card" data-detail="${esc(d.id)}">
  <span class="category-avatar">${iconFor(d.category)}</span>
  <span class="debt-main"><b>${esc(d.name)}</b><small>${esc(d.note||d.category)}${d.totalInstallments>1?` • Parcela ${d.installment}/${d.totalInstallments}`:''}</small></span>
  <span class="debt-side"><strong>${money(d.amount)}</strong><span class="status-pill ${s}">${dueText(d)}</span></span>
 </button>`;
}
function empty(text){return `<div class="empty-card">${esc(text)}</div>`;}

function renderHome(){
 const pending=pendingDebts(), late=pending.filter(d=>statusOf(d)==='late'), week=pending.filter(d=>{const n=dayDiff(d.due);return n>=0&&n<=7}), today=pending.filter(d=>statusOf(d)==='today');
 const paid=monthDebts().filter(d=>d.status==='paid');
 $('#monthLabel').textContent=`Resumo de ${monthName(new Date())}`;
 $('#userName').textContent=data.settings.userName||'Você';
 $('#totalPending').textContent=money(pending.reduce((a,d)=>a+d.amount,0));$('#pendingCount').textContent=`${pending.length} ${pending.length===1?'conta':'contas'}`;
 $('#lateTotal').textContent=money(late.reduce((a,d)=>a+d.amount,0));$('#lateCount').textContent=`${late.length} ${late.length===1?'conta':'contas'}`;
 $('#weekTotal').textContent=money(week.reduce((a,d)=>a+d.amount,0));$('#weekCount').textContent=`${week.length} ${week.length===1?'conta':'contas'}`;
 $('#paidMonth').textContent=money(paid.reduce((a,d)=>a+d.amount,0));$('#paidCount').textContent=`${paid.length} ${paid.length===1?'conta':'contas'}`;
 $('#todayTotal').textContent=money(today.reduce((a,d)=>a+d.amount,0));$('#todayCount').textContent=`${today.length} ${today.length===1?'conta':'contas'}`;
 const upcoming=[...pending].filter(d=>dayDiff(d.due)>=0).sort((a,b)=>a.due.localeCompare(b.due)).slice(0,5);
 $('#upcomingList').innerHTML=upcoming.length?upcoming.map(card).join(''):empty('Nenhum vencimento próximo. 🎉');
 $('#notificationDot').style.display=pending.some(d=>data.settings.reminders.includes(Math.max(0,dayDiff(d.due))))?'block':'none';
}

function renderDebts(){
 let list=[...data.debts];
 if(debtFilter!=='all') list=list.filter(d=>statusOf(d)===debtFilter || (debtFilter==='pending'&&statusOf(d)==='today'));
 list.sort((a,b)=>a.due.localeCompare(b.due));
 $('#debtList').innerHTML=list.length?list.map(card).join(''):empty('Nenhuma dívida neste filtro.');
}

function renderHistory(){
 const month=monthDebts(), paidMonth=month.filter(d=>d.status==='paid');
 $('#historyMonthTotal').textContent=money(paidMonth.reduce((a,d)=>a+d.amount,0));
 $('#historyMonthCount').textContent=`${paidMonth.length} ${paidMonth.length===1?'pagamento':'pagamentos'}`;
 $('#historyPaidRatio').textContent=`${paidMonth.length} de ${month.length}`;
 $('#historyPaidPercent').textContent=`${month.length?Math.round(paidMonth.length/month.length*100):0}% do mês`;
 let list=[...data.debts]; if(historyFilter!=='all') list=list.filter(d=>statusOf(d)===historyFilter || (historyFilter==='pending'&&statusOf(d)==='today'));
 list.sort((a,b)=>{const ad=a.paidAt||a.due,bd=b.paidAt||b.due;return historyNewest?bd.localeCompare(ad):ad.localeCompare(bd);});
 $('#historyList').innerHTML=list.length?list.map(card).join(''):empty('Nenhuma movimentação neste filtro.');
 $('#historySortBtn').textContent=historyNewest?'Mais recente ↓':'Mais antigo ↑';
}

function renderCalendar(){
 const y=calDate.getFullYear(),m=calDate.getMonth(); $('#calendarLabel').textContent=monthName(new Date(y,m,1));
 const first=new Date(y,m,1), start=new Date(y,m,1-first.getDay()); let html='';
 for(let i=0;i<42;i++){
   const d=new Date(start);d.setDate(start.getDate()+i);const iso=isoDate(d),items=data.debts.filter(x=>x.due===iso);
   const st=[...new Set(items.map(statusOf))]; const marks=st.map(s=>`<i class="mini-dot ${s==='today'?'today':s}"></i>`).join('');
   html+=`<button class="day ${d.getMonth()!==m?'muted':''} ${iso===selectedCalDay?'selected':''} ${iso===todayISO()?'today':''}" data-day="${iso}">${d.getDate()}<span class="marks">${marks}</span></button>`;
 }
 $('#calendarGrid').innerHTML=html; renderCalendarDay();
}
function renderCalendarDay(){
 const list=data.debts.filter(d=>d.due===selectedCalDay).sort((a,b)=>a.name.localeCompare(b.name));
 $('#selectedDateLabel').textContent=dateLong(selectedCalDay);$('#selectedDateCount').textContent=`${list.length} ${list.length===1?'conta':'contas'}`;
 $('#calendarDayList').innerHTML=list.length?list.map(card).join(''):empty(`Sem vencimentos em ${dateBR(selectedCalDay)}.`);
}

function renderCategorySelect(){
 const sel=$('#categorySelect'),current=sel.value; sel.innerHTML=data.categories.map(c=>`<option value="${esc(c)}">${esc(c)}</option>`).join(''); if(data.categories.includes(current))sel.value=current;
}
function renderCategories(){
 $('#categoryList').innerHTML=data.categories.map(c=>`<div class="category-row"><span class="field-icon blue">${iconFor(c)}</span><div><b>${esc(c)}</b></div><button data-delete-category="${esc(c)}" aria-label="Excluir">×</button></div>`).join('');
 renderCategorySelect();
}

function renderDetail(){
 const d=data.debts.find(x=>x.id===detailId);if(!d){go('home',false);return;}const s=statusOf(d),series=data.debts.filter(x=>x.seriesId===d.seriesId).sort((a,b)=>a.installment-b.installment);
 const total=d.totalInstallments>1?d.amount*d.totalInstallments:d.amount;
 $('#detailContent').innerHTML=`
 <div class="detail-hero">
  <div class="detail-title-row"><span class="category-avatar">${iconFor(d.category)}</span><div><h2>${esc(d.name)}</h2><p>${esc(d.note||d.category)}</p></div><span class="status-pill ${s}">${statusLabel(s)}</span></div>
  <div class="detail-value"><small>Valor ${d.totalInstallments>1?'da parcela':'da dívida'}</small><strong>${money(d.amount)}</strong></div>
  <div class="detail-grid">
   <div class="detail-box"><small>Vencimento</small><b>${dateBR(d.due)} • ${dueText(d)}</b></div>
   <div class="detail-box"><small>Categoria</small><b>${esc(d.category)}</b></div>
   <div class="detail-box"><small>Parcela</small><b>${d.totalInstallments>1?`${d.installment} de ${d.totalInstallments}`:'Única'}</b></div>
   <div class="detail-box"><small>Valor total</small><b>${money(total)}</b></div>
  </div>
 </div>
 ${series.length>1?`<div class="detail-section"><h3>Parcelas</h3>${series.map(x=>`<button class="installment-row" data-detail="${x.id}"><span><b>Parcela ${x.installment}/${x.totalInstallments}</b><small>${dateBR(x.due)} • ${statusLabel(statusOf(x))}</small></span><strong>${money(x.amount)}</strong></button>`).join('')}</div>`:''}
 <div class="detail-section"><h3>Lembretes ativados</h3><p style="color:var(--muted);font-size:12px;margin:0">${data.settings.reminders.sort((a,b)=>b-a).map(n=>n===0?'No vencimento':`${n} ${n===1?'dia':'dias'} antes`).join(' • ')||'Nenhum lembrete ativado'}</p></div>
 <div class="detail-actions">
  ${s!=='paid'?`<button class="primary-btn" data-pay="${d.id}">✓ Marcar como paga</button>`:`<button class="primary-btn" data-unpay="${d.id}">↶ Desfazer pagamento</button>`}
  <button class="secondary-btn" data-edit="${d.id}">Editar</button><button class="danger-btn" data-delete="${d.id}">Excluir</button>
 </div>`;
}

function renderReminders(){
 $$('[data-reminder]').forEach(cb=>cb.checked=data.settings.reminders.includes(Number(cb.dataset.reminder))); $('#soundToggle').checked=!!data.settings.sound;
 if(window.AndroidBridge) $('#enableNotificationsBtn').textContent='Notificações Android ativadas';
 else if('Notification' in window && Notification.permission==='granted') $('#enableNotificationsBtn').textContent='Notificações ativadas';
}
function renderSettings(){
 $('#settingsUserName').textContent=data.settings.userName||'Você'; $('#darkModeToggle').checked=!!data.settings.darkMode;document.body.classList.toggle('dark',!!data.settings.darkMode);
}
function renderAll(){renderHome();renderDebts();renderHistory();renderCalendar();renderCategories();renderReminders();renderSettings();if(currentView==='detail')renderDetail();save(false);}

const meta={
 home:null,debts:['Minhas dívidas','Todas as contas em um só lugar'],new:['Nova dívida','Cadastre uma conta ou parcela'],detail:['Detalhes da dívida','Informações e pagamentos'],calendar:null,history:null,
 reminders:['Lembretes','Nunca mais perca um vencimento'],settings:null,backup:['Backup e restauração','Proteja seus dados'],categories:['Categorias','Organize suas contas'],about:['Sobre o app','Dívida em Dia • Versão 1.0']
};
function go(view,push=true){
 if(!document.querySelector(`[data-view="${view}"]`))return;if(push&&currentView!==view)previousViews.push(currentView);currentView=view;
 $$('.view').forEach(v=>v.classList.toggle('active',v.dataset.view===view));
 const m=meta[view]; $('#brandHeader').classList.toggle('hidden',!!m);$('#pageHeading').classList.toggle('hidden',!m);$('#backBtn').classList.toggle('hidden',!m);
 if(m){$('#pageTitle').textContent=m[0];$('#pageSubtitle').textContent=m[1]||'';} $('#notificationBtn').classList.toggle('hidden',view==='new'||view==='detail'||view==='backup'||view==='categories'||view==='about'||view==='reminders');
 $$('.nav-item').forEach(b=>b.classList.toggle('active',b.dataset.go===view)); if(!['home','calendar','new','history','settings'].includes(view))$$('.nav-item').forEach(b=>b.classList.remove('active'));
 if(view==='detail')renderDetail();if(view==='new')renderNewPreview();if(view==='calendar')renderCalendar();if(view==='history')renderHistory();if(view==='settings')renderSettings();if(view==='reminders')renderReminders();
 window.scrollTo({top:0,behavior:'auto'});
}
function back(){const target=previousViews.pop()||'home';go(target,false)}

function toast(msg){const t=$('#toast');t.textContent=msg;t.classList.add('show');clearTimeout(toast.timer);toast.timer=setTimeout(()=>t.classList.remove('show'),2300)}
function dialog(title,text,{inputValue=null,confirm='OK',cancel='Cancelar'}={}){return new Promise(resolve=>{const b=$('#dialogBackdrop'),d=$('#dialog');d.innerHTML=`<h3>${esc(title)}</h3><p>${esc(text)}</p>${inputValue!==null?`<input id="dialogInput" value="${esc(inputValue)}">`:''}<div class="dialog-actions"><button class="secondary-btn" data-dialog-cancel>${esc(cancel)}</button><button class="primary-btn" data-dialog-ok>${esc(confirm)}</button></div>`;b.classList.remove('hidden');$('[data-dialog-cancel]').onclick=()=>{b.classList.add('hidden');resolve(null)};$('[data-dialog-ok]').onclick=()=>{const val=inputValue!==null?$('#dialogInput').value:true;b.classList.add('hidden');resolve(val)};});}

function renderNewPreview(){
 const f=$('#debtForm'), name=f.elements.name.value.trim()||'Nova dívida', raw=f.elements.amount.value, amount=parseMoney(raw), due=f.elements.due.value,cat=f.elements.category.value||data.categories[0],installments=$('#installmentsToggle').checked?Math.max(2,Number(f.elements.installments.value||2)):1;
 $('#newDebtPreview').innerHTML=`<small>Pré-visualização</small><div><span class="category-avatar">${iconFor(cat)}</span><div><b>${esc(name)}</b><em>${esc(cat)}${installments>1?` • ${installments} parcelas`:''}${due?` • 1º vencimento ${dateBR(due)}`:''}</em></div><strong>${money(amount)}</strong></div>`;
}
function parseMoney(v){let s=String(v||'').trim().replace(/\s/g,'');if(!s)return 0;if(s.includes(',')&&s.includes('.'))s=s.replace(/\./g,'').replace(',','.');else if(s.includes(','))s=s.replace(',','.');return Number(s.replace(/[^0-9.-]/g,''))||0;}
function resetForm(){const f=$('#debtForm');f.reset();f.elements.installments.value=2;$('#installmentsToggle').checked=false;$('#installmentsField').classList.add('hidden');if(!f.elements.due.value){const d=new Date();d.setDate(d.getDate()+1);f.elements.due.value=isoDate(d)};renderCategorySelect();renderNewPreview();}

function payDebt(id){const d=data.debts.find(x=>x.id===id);if(!d)return;d.status='paid';d.paidAt=todayISO();
 if(d.recurring&&d.totalInstallments===1){const nextDue=addMonths(d.due,1);if(!data.debts.some(x=>x.seriesId===d.seriesId&&x.due===nextDue)){data.debts.push({...d,id:uuid(),due:nextDue,status:'pending',paidAt:null,installment:1});}}
 save();renderAll();renderDetail();toast('Pagamento registrado.');
}
function unpayDebt(id){const d=data.debts.find(x=>x.id===id);if(!d)return;d.status='pending';d.paidAt=null;save();renderAll();renderDetail();toast('Pagamento desfeito.');}
async function deleteDebt(id){const d=data.debts.find(x=>x.id===id);if(!d)return;const ok=await dialog('Excluir dívida',`Deseja excluir ${d.name}?`,{confirm:'Excluir'});if(!ok)return;data.debts=data.debts.filter(x=>x.id!==id);save();renderAll();toast('Dívida excluída.');back();}
async function editDebt(id){const d=data.debts.find(x=>x.id===id);if(!d)return;const val=await dialog('Editar valor','Informe o novo valor desta parcela.',{inputValue:String(d.amount).replace('.',','),confirm:'Salvar'});if(val===null)return;const n=parseMoney(val);if(n<=0){toast('Valor inválido.');return;}d.amount=n;save();renderAll();renderDetail();toast('Valor atualizado.');}

function nativeAvailable(){return typeof window.AndroidBridge!=='undefined'}
function syncNativeReminders(){
 if(!nativeAvailable())return;try{window.AndroidBridge.syncReminders(JSON.stringify({settings:data.settings,debts:data.debts.filter(d=>d.status!=='paid')}));}catch(e){console.warn('Native reminder sync failed',e)}
}
async function enableNotifications(){
 if(nativeAvailable()){try{window.AndroidBridge.requestNotificationPermission();syncNativeReminders();toast('Lembretes do Android configurados.');return;}catch(e){}}
 if(!('Notification'in window)){toast('Notificações não são suportadas neste navegador.');return;}
 const p=await Notification.requestPermission();toast(p==='granted'?'Notificações ativadas.':'Permissão de notificação não concedida.');if(p==='granted')checkWebReminders(true);
}
function checkWebReminders(force=false){
 if(nativeAvailable()||!('Notification'in window)||Notification.permission!=='granted')return;const today=todayISO();
 data.debts.filter(d=>d.status!=='paid').forEach(d=>{const n=dayDiff(d.due);if(!data.settings.reminders.includes(n))return;const key=`ded-notif-${today}-${d.id}-${n}`;if(!force&&localStorage.getItem(key))return;const when=n===0?'vence hoje':n===1?'vence amanhã':`vence em ${n} dias`;new Notification('Dívida em Dia',{body:`${d.name} ${when} • ${money(d.amount)}`,icon:'icons/icon-192.png'});localStorage.setItem(key,'1');});
}
function saveTextFile(filename,type,content){
 if(nativeAvailable()&&window.AndroidBridge.saveTextFile){try{window.AndroidBridge.saveTextFile(filename,type,content);toast('Arquivo salvo em Downloads.');return; }catch(e){}}
 const blob=new Blob([content],{type}),a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download=filename;document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(a.href),1000);
}
function exportBackup(){saveTextFile(`divida-em-dia-backup-${todayISO()}.json`,'application/json',JSON.stringify(data,null,2));}
function csvEscape(v){const s=String(v??'');return /[",\n;]/.test(s)?`"${s.replace(/"/g,'""')}"`:s;}
function exportCsv(){const rows=[['Nome','Valor','Vencimento','Categoria','Status','Parcela','Total de parcelas','Pago em','Observação'],...data.debts.map(d=>[d.name,d.amount.toFixed(2).replace('.',','),dateBR(d.due),d.category,statusLabel(statusOf(d)),d.installment,d.totalInstallments,d.paidAt?dateBR(d.paidAt):'',d.note||''])];saveTextFile(`divida-em-dia-${todayISO()}.csv`,'text/csv;charset=utf-8','\uFEFF'+rows.map(r=>r.map(csvEscape).join(';')).join('\n'));}
function printReport(){
 let old=$('.report-only');if(old)old.remove();const el=document.createElement('section');el.className='report-only';const pending=pendingDebts();el.innerHTML=`<h1>Dívida em Dia</h1><p>Relatório gerado em ${new Date().toLocaleString('pt-BR')} • Desenvolvido por Thiers Lima</p><p><b>Total pendente:</b> ${money(pending.reduce((a,d)=>a+d.amount,0))}</p><table><thead><tr><th>Conta</th><th>Valor</th><th>Vencimento</th><th>Status</th></tr></thead><tbody>${data.debts.sort((a,b)=>a.due.localeCompare(b.due)).map(d=>`<tr><td>${esc(d.name)}</td><td>${money(d.amount)}</td><td>${dateBR(d.due)}</td><td>${statusLabel(statusOf(d))}</td></tr>`).join('')}</tbody></table>`;document.body.appendChild(el);window.print();}

// Eventos globais
addEventListener('click',async e=>{
 const nav=e.target.closest('[data-go]');if(nav){previousViews=[];go(nav.dataset.go,true);if(nav.dataset.go==='new')resetForm();return;}
 if(e.target.closest('#backBtn')){back();return;}
 const d=e.target.closest('[data-detail]');if(d){detailId=d.dataset.detail;go('detail');return;}
 const df=e.target.closest('[data-debt-filter]');if(df){debtFilter=df.dataset.debtFilter;$$('[data-debt-filter]').forEach(x=>x.classList.toggle('active',x===df));renderDebts();return;}
 const hf=e.target.closest('[data-history-filter]');if(hf){historyFilter=hf.dataset.historyFilter;$$('[data-history-filter]').forEach(x=>x.classList.toggle('active',x===hf));renderHistory();return;}
 const day=e.target.closest('[data-day]');if(day){selectedCalDay=day.dataset.day;renderCalendar();return;}
 const pay=e.target.closest('[data-pay]');if(pay){payDebt(pay.dataset.pay);return;}const un=e.target.closest('[data-unpay]');if(un){unpayDebt(un.dataset.unpay);return;}
 const del=e.target.closest('[data-delete]');if(del){await deleteDebt(del.dataset.delete);return;}const edit=e.target.closest('[data-edit]');if(edit){await editDebt(edit.dataset.edit);return;}
 const shortcut=e.target.closest('[data-filter-shortcut]');if(shortcut){const f=shortcut.dataset.filterShortcut;if(f==='paid'){historyFilter='paid';go('history');}else{debtFilter=f==='week'||f==='today'?'pending':f;go('debts');}return;}
 const dc=e.target.closest('[data-delete-category]');if(dc){if(data.categories.length<=1){toast('Mantenha pelo menos uma categoria.');return;}const cat=dc.dataset.deleteCategory;if(data.debts.some(x=>x.category===cat)){toast('Essa categoria está sendo usada por uma dívida.');return;}data.categories=data.categories.filter(x=>x!==cat);save();renderCategories();return;}
});

$('#notificationBtn').addEventListener('click',()=>go('reminders'));
$('#prevMonth').addEventListener('click',()=>{calDate=new Date(calDate.getFullYear(),calDate.getMonth()-1,1);renderCalendar()});
$('#nextMonth').addEventListener('click',()=>{calDate=new Date(calDate.getFullYear(),calDate.getMonth()+1,1);renderCalendar()});
$('#todayBtn').addEventListener('click',()=>{calDate=new Date();selectedCalDay=todayISO();renderCalendar()});
$('#historySortBtn').addEventListener('click',()=>{historyNewest=!historyNewest;renderHistory()});
$('#installmentsToggle').addEventListener('change',e=>{$('#installmentsField').classList.toggle('hidden',!e.target.checked);renderNewPreview()});
$('#debtForm').addEventListener('input',renderNewPreview);$('#debtForm').addEventListener('change',renderNewPreview);
$('#debtForm').addEventListener('submit',e=>{
 e.preventDefault();const f=new FormData(e.target),amount=parseMoney(f.get('amount')),name=String(f.get('name')||'').trim(),due=f.get('due'),category=f.get('category'),note=String(f.get('note')||'').trim();if(!name||!due||amount<=0){toast('Preencha nome, valor e vencimento.');return;}
 const installments=$('#installmentsToggle').checked?Math.max(2,Math.min(120,Number(f.get('installments')||2))):1;const recurring=!!f.get('recurring')&&installments===1;const seriesId=uuid();
 for(let i=0;i<installments;i++)data.debts.push({id:uuid(),seriesId,name,amount,due:addMonths(due,i),category,status:'pending',note,installment:i+1,totalInstallments:installments,recurring,paidAt:null});
 save();renderAll();toast(installments>1?`${installments} parcelas cadastradas.`:'Dívida cadastrada.');previousViews=[];go('home',false);resetForm();
});
$$('[data-reminder]').forEach(cb=>cb.addEventListener('change',()=>{const n=Number(cb.dataset.reminder);data.settings.reminders=cb.checked?[...new Set([...data.settings.reminders,n])]:data.settings.reminders.filter(x=>x!==n);data.settings.reminders.sort((a,b)=>b-a);save();renderReminders();toast('Lembretes atualizados.')}));
$('#soundToggle').addEventListener('change',e=>{data.settings.sound=e.target.checked;save();toast('Preferência salva.');});
$('#darkModeToggle').addEventListener('change',e=>{data.settings.darkMode=e.target.checked;save(false);renderSettings();toast(e.target.checked?'Modo escuro ativado.':'Modo claro ativado.');});
$('#enableNotificationsBtn').addEventListener('click',enableNotifications);
$('#backupBtn').addEventListener('click',exportBackup);$('#csvBtn').addEventListener('click',exportCsv);$('#printBtn').addEventListener('click',printReport);
$('#restoreInput').addEventListener('change',e=>{const file=e.target.files?.[0];if(!file)return;const r=new FileReader();r.onload=()=>{try{const parsed=JSON.parse(r.result);data=normalize(parsed);save();renderAll();toast('Backup restaurado com sucesso.');}catch(err){toast('Arquivo de backup inválido.');}finally{e.target.value=''}};r.readAsText(file);});
$('#categoryForm').addEventListener('submit',e=>{e.preventDefault();const input=e.target.elements.category,name=input.value.trim();if(!name)return;if(data.categories.some(c=>c.toLowerCase()===name.toLowerCase())){toast('Essa categoria já existe.');return;}data.categories.push(name);input.value='';save();renderCategories();toast('Categoria adicionada.');});
$('#editProfileBtn').addEventListener('click',async()=>{const v=await dialog('Seu nome','Como você quer ser chamado no app?',{inputValue:data.settings.userName||'',confirm:'Salvar'});if(v===null)return;const name=v.trim();if(name){data.settings.userName=name;save(false);renderAll();toast('Nome atualizado.');}});
$('#securityBtn').addEventListener('click',()=>dialog('Segurança','O Dívida em Dia funciona offline e mantém seus dados localmente neste aparelho. Nenhuma dívida é enviada para servidores.',{cancel:'Fechar',confirm:'Entendi'}));
$('#helpBtn').addEventListener('click',()=>dialog('Ajuda','Cadastre uma dívida, escolha o vencimento e ative os lembretes. Quando pagar, toque na dívida e marque como paga. Contas recorrentes criam o próximo mês automaticamente.',{cancel:'Fechar',confirm:'Entendi'}));

function startup(){
 renderCategorySelect();resetForm();renderAll();go('home',false);document.body.classList.toggle('dark',!!data.settings.darkMode);
 setTimeout(()=>$('#splash').classList.add('hide'),1050);syncNativeReminders();setTimeout(()=>checkWebReminders(false),1600);
 if('serviceWorker'in navigator&&location.protocol.startsWith('http'))navigator.serviceWorker.register('./sw.js').catch(()=>{});
}
window.appBack=()=>{if(currentView==='home'&&previousViews.length===0)return false;back();return true;};
startup();
})();
