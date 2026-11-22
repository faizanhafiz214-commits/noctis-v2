'use strict';
(()=>{
 const root=document.documentElement,btn=document.getElementById('themeSwitch'),pill=document.getElementById('serverStatus');
 const apply=theme=>{root.dataset.theme=theme;btn.textContent=theme==='dark'?'Light mode':'Dark mode';btn.setAttribute('aria-label',theme==='dark'?'Switch to light mode':'Switch to dark mode');localStorage.setItem('noctis_theme',theme);window.dispatchEvent(new Event('noctis-theme'))};
 apply(localStorage.getItem('noctis_theme')||'dark');btn.addEventListener('click',()=>apply(root.dataset.theme==='dark'?'light':'dark'));
 let connected=false;async function probe(){try{const response=await fetch('/api/health',{cache:'no-store',signal:AbortSignal.timeout(5000)});const json=await response.json();if(!response.ok||!json.ok)throw Error();pill.textContent='CONNECTED';pill.classList.add('connected');if(!connected)window.dispatchEvent(new Event('noctis-server-connected'));connected=true}catch{pill.textContent='SERVER WAKING UP';pill.classList.remove('connected');connected=false}setTimeout(probe,connected?30000:6000)}probe();
})();
