const http = require('http');
const crypto = require('crypto');
const { isPrivate } = require('./lan');

const PORT = 8765;

function esc(s) {
  return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

// The page a phone camera (or Google Lens) opens when it scans the QR.
function phonePage(host, key, valid) {
  const deep = `studymirror://pair?h=${encodeURIComponent(host)}&k=${encodeURIComponent(key)}`;
  const intent = `intent://pair?h=${encodeURIComponent(host)}&k=${encodeURIComponent(key)}#Intent;scheme=studymirror;package=com.mina.studymirror;end`;
  const body = valid
    ? `<h1>Link this phone</h1>
       <p>Your laptop is reachable. Tap the button to finish in Study Mirror.</p>
       <a class="btn" href="${esc(intent)}">Open Study Mirror</a>
       <p class="small">Button does nothing? <a href="${esc(deep)}">Try this link</a>, or in the app tap <b>Type the code</b> and enter:</p>
       <p class="code">${esc(host)}<br>key ${esc(key)}</p>`
    : `<h1>This code expired</h1><p>On the laptop press <b>Pair phone</b> again and scan the new QR.</p>`;
  return `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Study Mirror</title><style>
body{margin:0;min-height:100vh;display:grid;place-items:center;padding:24px;background:#080c10;color:#f0f6f4;font-family:system-ui,sans-serif;text-align:center}
main{max-width:380px}h1{margin:0 0 8px;font-size:26px}p{color:#9cb1c2;line-height:1.5}
.btn{display:block;margin:22px 0;padding:16px;border-radius:16px;background:#4f8cf6;color:#07121a;font-weight:700;font-size:18px;text-decoration:none}
.small{font-size:13px}.code{font:600 15px ui-monospace,monospace;color:#f0f6f4;background:rgba(255,255,255,.08);padding:12px;border-radius:12px}
a{color:#7aa7ff}</style></head><body><main>${body}</main></body></html>`;
}

function validPhone(ip, port, token) {
  return /^(\d{1,3}\.){3}\d{1,3}$/.test(ip || '') &&
    /^\d{4,5}$/.test(port || '') && +port >= 1024 && +port <= 65535 &&
    /^[A-Za-z0-9]{4,64}$/.test(token || '');
}

function createPairingServer({ onPair, port = PORT, log = () => {} }) {
  let key = crypto.randomBytes(6).toString('hex');
  let server = null;
  let error = '';

  const api = {
    get key() { return key; },
    get error() { return error; },
    get port() { return port; },
    rotate() { key = crypto.randomBytes(6).toString('hex'); return key; },

    start() {
      return new Promise(resolve => {
        server = http.createServer((req, res) => {
          const remote = (req.socket.remoteAddress || '').replace('::ffff:', '');
          const local = remote === '127.0.0.1' || remote === '::1';
          res.setHeader('Access-Control-Allow-Origin', '*');
          res.setHeader('Access-Control-Allow-Private-Network', 'true');
          res.setHeader('Cache-Control', 'no-store');
          if (!local && !isPrivate(remote)) { res.writeHead(403); return res.end(); }

          let url;
          try { url = new URL(req.url, 'http://x'); } catch (e) { res.writeHead(400); return res.end(); }
          const k = url.searchParams.get('k');

          if (url.pathname === '/ping') {
            res.writeHead(200, { 'Content-Type': 'application/json' });
            return res.end('{"ok":true,"app":"study-mirror"}');
          }
          if (url.pathname === '/p') {
            const host = (req.headers.host || '').replace(/[^\w.:\-]/g, '');
            res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
            return res.end(phonePage(host, k || '', !!k && k === key));
          }
          if (url.pathname === '/pair') {
            const ip = url.searchParams.get('ip');
            const p = url.searchParams.get('port') || '8080';
            const t = url.searchParams.get('t');
            if (!k || k !== key) {
              res.writeHead(403, { 'Content-Type': 'application/json' });
              return res.end('{"error":"expired or wrong key"}');
            }
            if (!validPhone(ip, p, t)) {
              res.writeHead(400, { 'Content-Type': 'application/json' });
              return res.end('{"error":"bad phone details"}');
            }
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end('{"ok":true}');
            api.rotate();                       // one use only
            onPair({ ip: `${ip}:${p}`, token: t });
            return;
          }
          res.writeHead(200, { 'Content-Type': 'text/plain' });
          res.end('Study Mirror pairing is running.');
        });
        server.on('error', e => {
          error = e.code === 'EADDRINUSE' ? `Port ${port} is already in use by another program.` : String(e.message || e);
          log(error);
          resolve(false);
        });
        server.listen(port, '0.0.0.0', () => { error = ''; log(`pairing on ${port}`); resolve(true); });
      });
    },

    stop() { try { server && server.close(); } catch (e) {} server = null; }
  };
  return api;
}

module.exports = { createPairingServer, PORT };
