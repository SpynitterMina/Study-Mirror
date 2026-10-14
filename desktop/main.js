const { app, BrowserWindow, ipcMain } = require('electron');
const path = require('path');
let win, saved = null;

function create() {
  win = new BrowserWindow({
    width: 460, height: 800, minWidth: 340, minHeight: 540,
    frame: false, backgroundColor: '#0e1a24', title: 'Study Mirror',
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true }
  });
  win.setMenuBarVisibility(false);
  win.loadFile('index.html');
}
app.whenReady().then(create);
app.on('window-all-closed', () => app.quit());

ipcMain.on('pin', (_e, on) => win.setAlwaysOnTop(!!on, 'screen-saver'));
ipcMain.on('mini', (_e, on) => {
  if (on) {
    if (win.isMaximized()) win.unmaximize();
    saved = win.getBounds();
    win.setMinimumSize(240, 84);
    win.setSize(300, 96);
    win.setAlwaysOnTop(true, 'screen-saver');
  } else {
    win.setMinimumSize(340, 540);
    if (saved) win.setBounds(saved);
  }
});
ipcMain.on('max', () => (win.isMaximized() ? win.unmaximize() : win.maximize()));
ipcMain.on('min', () => win.minimize());
ipcMain.on('close', () => win.close());
