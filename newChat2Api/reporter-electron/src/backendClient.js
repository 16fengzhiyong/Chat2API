function createBackendClient(configStore) {
  function netRequest(url, options = {}) {
    const { net } = require('electron')
    return new Promise((resolve, reject) => {
      const req = net.request({ method: options.method || 'GET', url })
      const headers = options.headers || {}
      for (const [key, value] of Object.entries(headers)) {
        req.setHeader(key, value)
      }
      req.on('response', (res) => {
        const chunks = []
        res.on('data', (chunk) => chunks.push(chunk))
        res.on('end', () => {
          const text = Buffer.concat(chunks).toString('utf8')
          resolve({
            status: res.statusCode,
            ok: res.statusCode >= 200 && res.statusCode < 300,
            text: () => Promise.resolve(text),
          })
        })
        res.on('error', reject)
      })
      req.on('error', reject)
      if (options.body) {
        req.write(options.body)
      }
      req.end()
    })
  }

  async function request(pathname, options = {}) {
    const current = configStore.get()
    const backendUrl = normalizeBackendUrl(current.backendUrl)
    const headers = {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    }
    if (current.clientId && current.secret) {
      headers['X-Reporter-Id'] = current.clientId
      headers['X-Reporter-Secret'] = current.secret
    }
    const response = await netRequest(`${backendUrl}${pathname}`, { ...options, headers })
    const text = await response.text()
    const payload = parsePayload(text)
    if (!response.ok) {
      throw new Error(errorMessage(payload, text, response.status))
    }
    if (payload && typeof payload === 'object' && 'success' in payload) {
      if (!payload.success) {
        throw new Error(payload.error?.message || 'Request failed')
      }
      return payload.data
    }
    return payload
  }

  function register(payload, version) {
    const current = configStore.get()
    const registrationCode = String(payload.registrationCode || current.registrationCode || '').trim()
    if (!registrationCode) {
      throw new Error('请先在管理端创建并复制启用的上报注册口令，然后填写到注册口令')
    }
    configStore.save({ backendUrl: payload.backendUrl, registrationCode })
    return request('/api/reporter/register', {
      method: 'POST',
      body: JSON.stringify({ name: payload.name || 'Desktop Reporter', version, registrationCode }),
    }).then((data) => configStore.save({ clientId: data.clientId, secret: data.secret }))
  }

  function heartbeat() {
    return request('/api/reporter/heartbeat', { method: 'POST' })
  }

  function uploadAccount(payload) {
    const normalizedPayload = {
      ...payload,
      credentials: normalizeProviderCredentials(payload?.providerId, payload?.credentials || {}),
    }
    return request('/api/reporter/accounts', { method: 'POST', body: JSON.stringify(normalizedPayload) })
  }

  return { register, heartbeat, uploadAccount }
}

function normalizeBackendUrl(value) {
  const url = String(value || 'http://localhost:8080').trim()
  return url.endsWith('/') ? url.slice(0, -1) : url
}

function parsePayload(text) {
  if (!text) {
    return null
  }
  try {
    return JSON.parse(text)
  } catch (_) {
    return text
  }
}

function errorMessage(payload, text, status) {
  const message = payload && typeof payload === 'object'
    ? payload.error?.message || payload.message || JSON.stringify(payload)
    : text || `Request failed with status ${status}`
  const translations = {
    'Reporter registration code is required': '请填写上报注册口令',
    'No enabled reporter registration code exists. Please create one in admin console first': '后端没有启用的上报注册口令，请先在管理端创建并启用一个口令',
    'Invalid reporter registration code. Please copy an enabled code from admin console': '上报注册口令无效，请从管理端复制当前启用的口令后重新填写',
    'Invalid reporter registration code': '上报注册口令无效，请从管理端复制当前启用的口令后重新填写',
  }
  if (translations[message]) {
    return translations[message]
  }
  if (payload && typeof payload === 'object') {
    return message
  }
  return message
}

function normalizeProviderCredentials(providerId, credentials) {
  const base = {}
  Object.entries(credentials || {}).forEach(([key, value]) => {
    if (value == null) {
      return
    }
    const text = String(value).trim()
    if (text) {
      base[key] = text
    }
  })
  if (providerId !== 'deepseek') {
    return base
  }
  const authorization = firstNonBlank(base.authorization, base.Authorization)
  const cookie = firstNonBlank(base.cookie, base.cookies)
  const token = firstNonBlank(
    unwrapDeepSeekToken(base.token),
    unwrapDeepSeekToken(base.userToken),
    unwrapDeepSeekToken(base.accessToken),
    unwrapDeepSeekToken(base.access_token),
    unwrapDeepSeekToken(base.apiKey),
    unwrapDeepSeekToken(base.refreshToken),
    unwrapDeepSeekToken(base.refresh_token),
    unwrapDeepSeekToken(authorization),
  )
  if (authorization) {
    base.authorization = authorization
    base.Authorization = authorization
  }
  if (cookie) {
    base.cookie = cookie
    base.cookies = cookie
  }
  if (token) {
    base.token = token
    base.userToken = token
  }
  return base
}

function unwrapDeepSeekToken(value) {
  const text = stripBearerPrefix(value)
  if (!text) {
    return ''
  }
  if ((text.startsWith('{') && text.endsWith('}')) || (text.startsWith('[') && text.endsWith(']'))) {
    try {
      const parsed = JSON.parse(text)
      const extracted = tokenFromObject(parsed)
      return extracted || ''
    } catch (_) {
      return text
    }
  }
  return text
}

function tokenFromObject(value) {
  if (value == null) {
    return ''
  }
  if (typeof value === 'string') {
    const normalized = stripBearerPrefix(value)
    return normalized && normalized !== 'null' && normalized !== 'undefined' ? normalized : ''
  }
  if (Array.isArray(value)) {
    for (const item of value) {
      const extracted = tokenFromObject(item)
      if (extracted) {
        return extracted
      }
    }
    return ''
  }
  if (typeof value !== 'object') {
    return ''
  }
  for (const key of ['value', 'token', 'accessToken', 'access_token', 'userToken', 'refreshToken', 'refresh_token']) {
    const extracted = tokenFromObject(value[key])
    if (extracted) {
      return extracted
    }
  }
  for (const key of ['data', 'biz_data', 'payload', 'result']) {
    const extracted = tokenFromObject(value[key])
    if (extracted) {
      return extracted
    }
  }
  return ''
}

function stripBearerPrefix(value) {
  return value == null ? '' : String(value).trim().replace(/^bearer\s+/i, '')
}

function firstNonBlank(...values) {
  return values.find((value) => value && String(value).trim()) || ''
}

module.exports = { createBackendClient }
