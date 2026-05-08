const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('reporterApi', {
  getConfig: () => ipcRenderer.invoke('config:get'),
  saveConfig: (config) => ipcRenderer.invoke('config:save', config),
  providers: () => ipcRenderer.invoke('providers:list'),
  register: (payload) => ipcRenderer.invoke('reporter:register', payload),
  heartbeat: () => ipcRenderer.invoke('reporter:heartbeat'),
  startLogin: (providerId) => ipcRenderer.invoke('login:start', providerId),
  uploadAccount: (payload) => ipcRenderer.invoke('account:upload', payload),
  onLog: (callback) => ipcRenderer.on('reporter:log', (_, message) => callback(message)),
})
