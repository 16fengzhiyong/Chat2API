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
    configStore.save({ backendUrl: payload.backendUrl })
    return request('/api/reporter/register', {
      method: 'POST',
      body: JSON.stringify({ name: payload.name || 'Desktop Reporter', version }),
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
  if (payload && typeof payload === 'object') {
    return payload.error?.message || payload.message || JSON.stringify(payload)
  }
  return text || `Request failed with status ${status}`
}

module.exports = { createBackendClient }
