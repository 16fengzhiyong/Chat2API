const { BrowserWindow, session } = require('electron')
const { normalizeProviderCredentials } = require('./credentialNormalizer')

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
          const extracted = await extractFromWindow(loginWindow, provider)
          const credentials = normalizeProviderCredentials(provider.id, { ...intercepted, ...extracted.credentials })
          const account = { ...extractAccountInfo(credentials), ...extracted.account }
          resolve({ providerId, credentials, account })
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
  const snapshot = await webContents.executeJavaScript(`
    (() => {
      const readStorage = (storage) => {
        const output = {};
        for (let i = 0; i < storage.length; i++) {
          const key = storage.key(i);
          output[key] = storage.getItem(key);
        }
        return output;
      };
      const domHints = Array.from(document.querySelectorAll('[title], [aria-label]')).slice(0, 200).map((element) => ({
        title: element.getAttribute('title') || '',
        ariaLabel: element.getAttribute('aria-label') || '',
        text: (element.textContent || '').trim().slice(0, 120),
      }));
      return {
        localStorage: readStorage(localStorage),
        sessionStorage: readStorage(sessionStorage),
        title: document.title || '',
        bodyText: document.body ? document.body.innerText.slice(0, 8000) : '',
        domHints,
      };
    })()
  `).catch(() => ({}))
  const storage = snapshot.localStorage || {}
  const sessionStorage = snapshot.sessionStorage || {}
  const allStorage = { ...storage, ...sessionStorage }
  const account = extractAccountInfo({
    ...allStorage,
    documentTitle: snapshot.title || '',
    documentText: snapshot.bodyText || '',
    domHints: snapshot.domHints || [],
  })
  const cookies = await session.fromPartition(`persist:login-${provider.id}`).cookies.get({}).catch(() => [])
  const cookieText = cookies.map((cookie) => `${cookie.name}=${cookie.value}`).join('; ')
  const credentials = {}
  provider.localStorageKeys.forEach((key) => {
    if (storage[key]) {
      credentials[key] = storage[key]
    } else if (sessionStorage[key]) {
      credentials[key] = sessionStorage[key]
    }
  })
  if (cookieText) {
    credentials.cookie = cookieText
  }
  return { credentials: normalizeProviderCredentials(provider.id, credentials), account }
}

function extractAccountInfo(source) {
  const candidates = { name: [], email: [] }
  visitProfileValue(source, [], candidates, 0)
  const email = firstUnique(candidates.email.map(normalizeText).filter(Boolean))
  const name = firstUnique(candidates.name.map(normalizeText).filter((value) => value && value !== email && isReasonableName(value)))
  return {
    ...(name ? { name } : {}),
    ...(email ? { email } : {}),
  }
}

function visitProfileValue(value, path, candidates, depth) {
  if (depth > 6 || value == null) {
    return
  }
  if (typeof value === 'string') {
    const text = value.trim()
    if (!text) {
      return
    }
    const email = extractEmail(text)
    if (email) {
      candidates.email.push(email)
      const nearbyName = nameNearEmail(text, email)
      if (nearbyName) {
        candidates.name.push(nearbyName)
      }
    }
    if (isLikelyNameKey(path) && isReasonableName(text)) {
      candidates.name.push(text)
    }
    const parsed = parseJsonObject(text)
    if (parsed) {
      visitProfileValue(parsed, path, candidates, depth + 1)
    }
    return
  }
  if (Array.isArray(value)) {
    value.forEach((item, index) => visitProfileValue(item, [...path, String(index)], candidates, depth + 1))
    return
  }
  if (typeof value === 'object') {
    Object.entries(value).forEach(([key, next]) => visitProfileValue(next, [...path, key], candidates, depth + 1))
  }
}

function parseJsonObject(value) {
  if (!value.startsWith('{') && !value.startsWith('[')) {
    return null
  }
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch (_) {
    return null
  }
}

function extractEmail(value) {
  const match = value.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)
  return match ? match[0] : ''
}

function nameNearEmail(text, email) {
  if (!text.includes('\n') || !email) {
    return ''
  }
  const lines = text.split(/\n+/).map(normalizeText).filter(Boolean)
  const index = lines.findIndex((line) => line.includes(email))
  if (index < 0) {
    return ''
  }
  const start = Math.max(0, index - 3)
  const end = Math.min(lines.length - 1, index + 3)
  for (let i = index - 1; i >= start; i -= 1) {
    if (isReasonableName(lines[i])) {
      return lines[i]
    }
  }
  for (let i = index + 1; i <= end; i += 1) {
    if (isReasonableName(lines[i])) {
      return lines[i]
    }
  }
  return ''
}

function isLikelyNameKey(path) {
  const key = String(path[path.length - 1] || '').toLowerCase()
  return ['name', 'nickname', 'nick_name', 'displayname', 'display_name', 'username', 'user_name', 'loginname', 'login_name', 'accountname', 'account_name'].includes(key)
}

function isReasonableName(value) {
  const text = normalizeText(value)
  if (!text || text.length > 80) {
    return false
  }
  if (extractEmail(text) || /^https?:\/\//i.test(text) || /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(text)) {
    return false
  }
  if (/[{}[\];=]/.test(text) || /^bearer\s+/i.test(text)) {
    return false
  }
  return true
}

function normalizeText(value) {
  return String(value || '').replace(/\s+/g, ' ').trim()
}

function firstUnique(values) {
  return values.find((value, index) => value && values.indexOf(value) === index) || ''
}

module.exports = { createLoginExtractor }
