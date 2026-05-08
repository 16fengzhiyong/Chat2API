const { app, BrowserWindow, ipcMain, session } = require('electron')
const fs = require('fs')
const path = require('path')
const { providers } = require('./providers')

let mainWindow
let loginWindow
let configFile
let configData = {}

function loadConfig() {
  configFile = path.join(app.getPath('userData'), 'reporter-config.json')
  try {
    configData = JSON.parse(fs.readFileSync(configFile, 'utf8'))
  } catch (_) {
    configData = {}
  }
}

function writeConfig() {
  fs.mkdirSync(path.dirname(configFile), { recursive: true })
  fs.writeFileSync(configFile, JSON.stringify(configData, null, 2))
}

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

function config() {
  return {
    backendUrl: configData.backendUrl || 'http://localhost:8080',
    clientId: configData.clientId || '',
    secret: configData.secret || '',
  }
}

function saveConfig(next) {
  Object.entries(next).forEach(([key, value]) => {
    configData[key] = value
  })
  writeConfig()
  return config()
}

async function backendFetch(pathname, options = {}) {
  const current = config()
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
  }
  if (current.clientId && current.secret) {
    headers['X-Reporter-Id'] = current.clientId
    headers['X-Reporter-Secret'] = current.secret
  }
  const response = await fetch(`${current.backendUrl}${pathname}`, { ...options, headers })
  const text = await response.text()
  if (!response.ok) {
    throw new Error(text)
  }
  return text ? JSON.parse(text) : null
}

function emit(channel, payload) {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send(channel, payload)
  }
}

async function extractFromWindow(provider) {
  const webContents = loginWindow.webContents
  const storage = await webContents.executeJavaScript(`
    (() => {
      const output = {};
      for (let i = 0; i < localStorage.length; i++) {
        const key = localStorage.key(i);
        output[key] = localStorage.getItem(key);
      }
      return output;
    })()
  `).catch(() => ({}))
  const cookies = await session.fromPartition(`persist:login-${provider.id}`).cookies.get({}).catch(() => [])
  const cookieText = cookies.map((cookie) => `${cookie.name}=${cookie.value}`).join('; ')
  const credentials = {}
  provider.localStorageKeys.forEach((key) => {
    if (storage[key]) {
      credentials[key] = storage[key]
    }
  })
  if (cookieText) {
    credentials.cookie = cookieText
  }
  return credentials
}

async function openLogin(providerId) {
  const provider = providers.find((item) => item.id === providerId)
  if (!provider) {
    throw new Error(`Unsupported provider: ${providerId}`)
  }
  if (loginWindow && !loginWindow.isDestroyed()) {
    loginWindow.close()
  }
  const partition = `persist:login-${provider.id}`
  const loginSession = session.fromPartition(partition)
  const intercepted = {}
  loginSession.webRequest.onBeforeSendHeaders((details, callback) => {
    const authorization = details.requestHeaders.Authorization || details.requestHeaders.authorization
    const cookie = details.requestHeaders.Cookie || details.requestHeaders.cookie
    if (authorization) intercepted.authorization = authorization
    if (cookie) intercepted.cookie = cookie
    callback({ requestHeaders: details.requestHeaders })
  })
  loginWindow = new BrowserWindow({
    width: 1200,
    height: 850,
    title: `${provider.name} Login`,
    webPreferences: {
      partition,
      contextIsolation: true,
      nodeIntegration: false,
    },
  })
  loginWindow.webContents.on('did-navigate', async (_, url) => {
    if (provider.successPatterns.some((pattern) => url.includes(pattern))) {
      emit('reporter:log', `已导航到 ${provider.name} 页面，正在尝试提取凭据`)
    }
  })
  await loginWindow.loadURL(provider.loginUrl)
  return new Promise((resolve, reject) => {
    let completed = false
    const complete = async () => {
      if (completed) {
        return
      }
      completed = true
      try {
        const credentials = { ...intercepted, ...(await extractFromWindow(provider)) }
        resolve({ providerId, credentials })
      } catch (error) {
        reject(error)
      }
    }
    const timeout = setTimeout(complete, 45000)
    loginWindow.on('close', async (event) => {
      if (!completed) {
        event.preventDefault()
        clearTimeout(timeout)
        await complete()
        loginWindow.destroy()
      }
    })
  })
}

ipcMain.handle('config:get', () => config())
ipcMain.handle('config:save', (_, next) => saveConfig(next))
ipcMain.handle('providers:list', () => providers)
ipcMain.handle('reporter:register', async (_, payload) => {
  saveConfig({ backendUrl: payload.backendUrl })
  const result = await backendFetch('/api/reporter/register', { method: 'POST', body: JSON.stringify({ name: payload.name || 'Desktop Reporter', version: app.getVersion() }) })
  const data = result.data || result
  return saveConfig({ clientId: data.clientId, secret: data.secret })
})
ipcMain.handle('reporter:heartbeat', async () => backendFetch('/api/reporter/heartbeat', { method: 'POST' }))
ipcMain.handle('login:start', async (_, providerId) => openLogin(providerId))
ipcMain.handle('account:upload', async (_, payload) => backendFetch('/api/reporter/accounts', { method: 'POST', body: JSON.stringify(payload) }))

app.whenReady().then(() => {
  loadConfig()
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
