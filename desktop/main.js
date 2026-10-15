const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');
const { execFile } = require('child_process');
const QRCode = require('qrcode');
const { getLocalIps } = require('./lan');
const { createPairingServer, PORT } = require('./pairing');

let mainWindow;

const pairing = createPairingServer({
  onPair: info => mainWindow && mainWindow.webContents.send('phone-paired', info),
  log: m => console.log(m)
});

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 440,
    height: 740,
    minWidth: 360,
    minHeight: 520,
    frame: false,
    backgroundColor: '#080c10',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });
  mainWindow.loadFile('index.html');
  pairing.start();
}

app.whenReady().then(createWindow);
app.on('window-all-closed', () => { pairing.stop(); if (process.platform !== 'darwin') app.quit(); });

ipcMain.on('pin', (_, on) => mainWindow?.setAlwaysOnTop(!!on));
ipcMain.on('min', () => mainWindow?.minimize());
ipcMain.on('max', () => {
  if (!mainWindow) return;
  mainWindow.isMaximized() ? mainWindow.unmaximize() : mainWindow.maximize();
});
ipcMain.on('close', () => mainWindow?.close());
ipcMain.on('mini', (_, isMini) => {
  if (!mainWindow) return;
  if (isMini) {
    mainWindow.setMinimumSize(280, 75);
    mainWindow.setSize(320, 85);
    mainWindow.setAlwaysOnTop(true);
  } else {
    mainWindow.setMinimumSize(360, 520);
    mainWindow.setSize(440, 740);
  }
});

// A fresh one-use key every time the pairing window opens.
ipcMain.handle('get-pairing-info', async () => {
  if (pairing.error) await pairing.start();          // try again if the port was busy
  pairing.rotate();
  const ips = getLocalIps();
  return {
    ips,                                              // [{ ip, name }], best guess first
    port: PORT,
    key: pairing.key,
    error: pairing.error,
    urls: ips.map(x => `http://${x.ip}:${PORT}/p?k=${pairing.key}`)
  };
});

// Crisp vector QR, made here so it works offline.
ipcMain.handle('make-qr', async (_, text) => {
  return QRCode.toString(String(text), { type: 'svg', margin: 3, errorCorrectionLevel: 'M', color: { dark: '#000000', light: '#ffffff' } });
});

// Opens Windows' own permission prompt to allow the pairing port through the firewall.
ipcMain.handle('fix-firewall', async () => {
  if (process.platform !== 'win32') return { ok: false, reason: 'Windows only' };
  const cmd = `Start-Process netsh -ArgumentList 'advfirewall','firewall','add','rule','name=StudyMirrorPairing','dir=in','action=allow','protocol=TCP','localport=${PORT}','profile=private,public' -Verb RunAs`;
  return new Promise(resolve => {
    execFile('powershell.exe', ['-NoProfile', '-Command', cmd], err => resolve({ ok: !err, reason: err ? String(err.message) : '' }));
  });
});
