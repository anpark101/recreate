import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, access } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { once } from 'node:events';

const read = path => readFile(new URL('../' + path, import.meta.url), 'utf8');
test('manifest is a standalone same-origin launcher with valid icon dimensions', async () => {
  const manifest = JSON.parse(await read('web/manifest.webmanifest'));
  assert.equal(manifest.display, 'standalone');
  assert.equal(manifest.start_url, './');
  assert.equal(manifest.scope, './');
  for (const icon of manifest.icons) {
    const bytes = await readFile(new URL('../web/' + icon.src, import.meta.url));
    const [width, height] = icon.sizes.split('x').map(Number);
    assert.equal(bytes.readUInt32BE(16), width);
    assert.equal(bytes.readUInt32BE(20), height);
  }
});
test('service worker precache references existing files, not the remote website', async () => {
  const sw = await read('web/sw.js');
  const list = sw.match(/const FILES = \[(.*?)\];/s)[1];
  for (const [, name] of list.matchAll(/'([^']+)'/g)) {
    assert.ok(!name.startsWith('https:'));
    await access(new URL('../web/' + (name === './' ? 'index.html' : name), import.meta.url));
  }
  assert.match(sw, /url.origin !== self.location.origin/);
});
test('Android loads original URL and uses no JavaScript native interface', async () => {
  const java = await read('android/app/src/main/java/app/superyau/MainActivity.java');
  assert.match(java, /https:\/\/superyau\.iryonmann\.workers\.dev\//);
  assert.match(java, /setJavaScriptEnabled\(true\)/);
  assert.match(java, /setDomStorageEnabled\(true\)/);
  assert.match(java, /handler.cancel\(\)/);
  assert.doesNotMatch(java, /addJavascriptInterface|handler.proceed/);
  assert.match(java, /ACTION_CREATE_DOCUMENT/);
});
test('server serves launcher, JS, manifest and icons, and rejects missing files', async t => {
  const server = spawn(process.execPath, ['scripts/serve.mjs'], { env: { ...process.env, PORT: '0' } });
  t.after(() => server.kill());
  const [output] = await once(server.stdout, 'data');
  const port = output.toString().match(/port (\d+)/)[1];
  const base = 'http://127.0.0.1:' + port;
  for (const path of ['/', '/install.html', '/app.js', '/sw.js', '/manifest.webmanifest', '/icons/icon-192.png']) {
    assert.equal((await fetch(base + path)).status, 200, path);
  }
  assert.match(await (await fetch(base)).text(), /<iframe[^>]+src="https:\/\/superyau\.iryonmann\.workers\.dev\//);
  assert.equal((await fetch(base + '/missing-file')).status, 404);
  assert.equal((await fetch(base + '/%2e%2e%2fREADME.md')).status, 403);
  assert.equal((await fetch(base + '/manifest.webmanifest')).headers.get('content-type'), 'application/manifest+json');
});
