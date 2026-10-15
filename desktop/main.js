const { app, BrowserWindow, ipcMain, screen } = require('electron');
const path = require('path');
const { execFile } = require('child_process');
const QRCode = require('qrcode');
const { getLocalIps } = require('./lan');
const { createPairingServer, PORT } = require('./pairing');

let mainWindow;
let miniState = null;
app.setAppUserModelId('com.mina.studymirror.control');

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
    title: 'Study Mirror',
    icon: path.join(__dirname, 'build', 'icon.png'),
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
    if (miniState) return;
    miniState = { bounds: mainWindow.getNormalBounds(), max: mainWindow.isMaximized(), full: mainWindow.isFullScreen() };
    if (miniState.full) mainWindow.setFullScreen(false);
    if (miniState.max) mainWindow.unmaximize();
    const apply = () => {
      const wa = screen.getDisplayMatching(miniState.bounds).workArea;
      mainWindow.setMinimumSize(280, 72);
      mainWindow.setBounds({ x: wa.x + wa.width - 340, y: wa.y + 24, width: 320, height: 84 });
      mainWindow.setAlwaysOnTop(true, 'screen-saver');
    };
    if (miniState.max || miniState.full) setTimeout(apply, 150); else apply();
  } else {
    if (!miniState) return;
    const st = miniState;
    miniState = null;
    mainWindow.setMinimumSize(360, 520);
    mainWindow.setBounds(st.bounds);
    if (st.max) mainWindow.maximize();
    if (st.full) mainWindow.setFullScreen(true);
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
