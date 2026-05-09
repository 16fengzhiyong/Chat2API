const { app, BrowserWindow, ipcMain } = require('electron')
const path = require('path')
const { createBackendClient } = require('./backendClient')
const { createConfigStore } = require('./configStore')
const { createLoginExtractor } = require('./loginExtractor')
const { providers } = require('./providers')

let mainWindow
let configStore
let backendClient
let loginExtractor

function createMainWindow() {
  mainWindow = new BrowserWindow({
    width: 1120,
    height: 820,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  })
  mainWindow.loadFile(path.join(__dirname, 'renderer', 'index.html'))
}

function emit(channel, payload) {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send(channel, payload)
  }
}

function registerIpcHandlers() {
  ipcMain.handle('config:get', () => configStore.get())
  ipcMain.handle('config:save', (_, next) => configStore.save(next))
  ipcMain.handle('providers:list', () => providers)
  ipcMain.handle('reporter:register', (_, payload) => backendClient.register(payload, app.getVersion()))
  ipcMain.handle('reporter:heartbeat', () => backendClient.heartbeat())
  ipcMain.handle('login:start', (_, providerId) => loginExtractor.openLogin(providerId))
  ipcMain.handle('account:upload', (_, payload) => backendClient.uploadAccount(payload))
}

app.whenReady().then(() => {
  configStore = createConfigStore(app)
  configStore.load()
  backendClient = createBackendClient(configStore)
  loginExtractor = createLoginExtractor(providers, emit)
  registerIpcHandlers()
  createMainWindow()
})
app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})
app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    createMainWindow()
  }
})
