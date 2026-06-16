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
    extractTokenValue(base.token),
    extractTokenValue(base.userToken),
    extractTokenValue(base.accessToken),
    extractTokenValue(base.access_token),
    extractTokenValue(base.apiKey),
    extractTokenValue(base.refreshToken),
    extractTokenValue(base.refresh_token),
    extractTokenValue(authorization),
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
  } else {
    for (const k of ['token', 'userToken', 'accessToken', 'access_token', 'apiKey', 'refreshToken', 'refresh_token']) {
      delete base[k]
    }
  }
  return base
}

function extractTokenValue(value) {
  const stripped = stripBearerPrefix(value)
  if (!stripped) {
    return ''
  }
  const parsed = parseJsonObject(stripped)
  if (parsed) {
    const extracted = tokenFromObject(parsed, 0)
    return extracted || ''
  }
  return stripped
}

function tokenFromObject(value, depth) {
  if (depth > 5 || value == null) {
    return ''
  }
  if (typeof value === 'string') {
    const parsed = parseJsonObject(value)
    if (parsed) {
      return tokenFromObject(parsed, depth + 1)
    }
    const normalized = stripBearerPrefix(value)
    return normalized && normalized !== 'null' && normalized !== 'undefined' ? normalized : ''
  }
  if (Array.isArray(value)) {
    for (const item of value) {
      const extracted = tokenFromObject(item, depth + 1)
      if (extracted) {
        return extracted
      }
    }
    return ''
  }
  if (typeof value !== 'object') {
    return ''
  }
  const directKeys = ['value', 'token', 'accessToken', 'access_token', 'userToken', 'refreshToken', 'refresh_token']
  for (const key of directKeys) {
    const extracted = tokenFromObject(value[key], depth + 1)
    if (extracted) {
      return extracted
    }
  }
  for (const nestedKey of ['data', 'biz_data', 'payload', 'result']) {
    const extracted = tokenFromObject(value[nestedKey], depth + 1)
    if (extracted) {
      return extracted
    }
  }
  return ''
}

function parseJsonObject(value) {
  const text = String(value || '').trim()
  if ((!text.startsWith('{') || !text.endsWith('}')) && (!text.startsWith('[') || !text.endsWith(']'))) {
    return null
  }
  try {
    const parsed = JSON.parse(text)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch (_) {
    return null
  }
}

function stripBearerPrefix(value) {
  return value == null ? '' : String(value).trim().replace(/^bearer\s+/i, '')
}

function firstNonBlank(...values) {
  return values.find((value) => value && String(value).trim()) || ''
}

module.exports = {
  normalizeProviderCredentials,
  extractTokenValue,
}
