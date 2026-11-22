'use strict';
const offline = document.getElementById('offline');
const website = document.getElementById('website');
function updateConnection() { offline.hidden = navigator.onLine; }
window.addEventListener('offline', updateConnection);
window.addEventListener('online', updateConnection);
document.getElementById('retry').addEventListener('click', () => {
  updateConnection();
  if (navigator.onLine) website.src = 'https://superyau.iryonmann.workers.dev/';
});
updateConnection();
if ('serviceWorker' in navigator) navigator.serviceWorker.register('./sw.js').catch(console.warn);
