'use strict';
const install = document.getElementById('install');
const status = document.getElementById('status');
let promptEvent;
window.addEventListener('beforeinstallprompt', event => {
  event.preventDefault();
  promptEvent = event;
  install.hidden = false;
});
install.addEventListener('click', async () => {
  if (!promptEvent) return;
  await promptEvent.prompt();
  const { outcome } = await promptEvent.userChoice;
  status.textContent = outcome === 'accepted' ? 'Installation requested. Follow Chrome’s instructions.' : 'You can install later from Chrome’s menu.';
  promptEvent = undefined;
  install.hidden = true;
});
window.addEventListener('appinstalled', () => {
  install.hidden = true;
  status.textContent = 'Superyau is installed.';
});
if ('serviceWorker' in navigator) navigator.serviceWorker.register('./sw.js').catch(() => { status.textContent = 'Installation needs HTTPS (or localhost for development).'; });
