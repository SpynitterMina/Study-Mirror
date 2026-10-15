const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('sm', {
  send: (channel, data) => ipcRenderer.send(channel, data),
  on: (channel, callback) => ipcRenderer.on(channel, (_, ...args) => callback(...args)),
  getPairingInfo: () => ipcRenderer.invoke('get-pairing-info'),
  makeQr: text => ipcRenderer.invoke('make-qr', text),
  fixFirewall: () => ipcRenderer.invoke('fix-firewall')
});
