const { BrowserWindow, session } = require('electron')

function createLoginExtractor(providers, emit) {
  let loginWindow

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
    loginWindow.webContents.on('did-navigate', (_, url) => {
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
          const credentials = { ...intercepted, ...(await extractFromWindow(loginWindow, provider)) }
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

  return { openLogin }
}

async function extractFromWindow(loginWindow, provider) {
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

module.exports = { createLoginExtractor }
