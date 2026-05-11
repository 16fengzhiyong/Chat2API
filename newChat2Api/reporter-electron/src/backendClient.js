function createBackendClient(configStore) {
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
    const response = await fetch(`${backendUrl}${pathname}`, { ...options, headers })
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
    return request('/api/reporter/accounts', { method: 'POST', body: JSON.stringify(payload) })
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

module.exports = { createBackendClient }
