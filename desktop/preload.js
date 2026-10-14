const { contextBridge, ipcRenderer } = require('electron');
const ok = ['pin', 'mini', 'max', 'min', 'close'];
contextBridge.exposeInMainWorld('sm', { send: (c, v) => { if (ok.includes(c)) ipcRenderer.send(c, v); } });
