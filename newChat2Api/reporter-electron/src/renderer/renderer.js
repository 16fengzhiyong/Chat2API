const backendUrlInput = document.getElementById('backendUrl')
const registrationCodeInput = document.getElementById('registrationCode')
const registerBtn = document.getElementById('registerBtn')
const heartbeatBtn = document.getElementById('heartbeatBtn')
const providerSelect = document.getElementById('provider')
const providerHint = document.getElementById('providerHint')
const accountNameInput = document.getElementById('accountName')
const emailInput = document.getElementById('email')
const loginBtn = document.getElementById('loginBtn')
const uploadBtn = document.getElementById('uploadBtn')
const credentialsInput = document.getElementById('credentials')
const clientInfo = document.getElementById('clientInfo')
const clientTitle = document.getElementById('clientTitle')
const connectionBadge = document.getElementById('connectionBadge')
const credentialBadge = document.getElementById('credentialBadge')
const clearLogBtn = document.getElementById('clearLogBtn')
const logOutput = document.getElementById('log')

const state = {
  config: null,
  providers: [],
  credentials: {},
  busy: false,
}

function log(message, payload) {
  const detail = payload ? `\n${typeof payload === 'string' ? payload : JSON.stringify(payload, null, 2)}` : ''
  const line = `[${new Date().toLocaleTimeString()}] ${message}${detail}`
  logOutput.textContent = `${line}\n${logOutput.textContent}`
}

async function init() {
  const config = await window.reporterApi.getConfig()
  state.config = config
  const providers = await window.reporterApi.providers()
  state.providers = providers
  renderConfig()
  renderProviders()
  renderCredentials()
  setBusy(false)
  if (state.config && state.config.clientId && state.config.secret) {
    startHeartbeatTimer()
  }
}

let heartbeatTimer = null

function startHeartbeatTimer() {
  if (heartbeatTimer) clearInterval(heartbeatTimer)
  heartbeatTimer = setInterval(async () => {
    const config = state.config || {}
    if (!config.clientId || !config.secret) return
    try {
      await window.reporterApi.heartbeat()
      state.config = await window.reporterApi.getConfig()
      renderConfig('online')
      log('自动心跳成功')
    } catch (error) {
      renderConfig('error')
      log('自动心跳失败', getErrorMessage(error))
    }
  }, 5 * 60 * 1000)
}

async function handleRegister() {
  try {
    setBusy(true)
    const config = await window.reporterApi.register({ backendUrl: backendUrlInput.value.trim(), name: 'Desktop Reporter', registrationCode: registrationCodeInput.value })
    state.config = config
    registrationCodeInput.value = ''
    renderConfig()
    log('上报端注册成功')
    startHeartbeatTimer()
  } catch (error) {
    log('注册失败', getErrorMessage(error))
  } finally {
    setBusy(false)
  }
}

async function handleHeartbeat() {
  try {
    setBusy(true)
    state.config = await window.reporterApi.saveConfig({ backendUrl: backendUrlInput.value })
    const result = await window.reporterApi.heartbeat()
    state.config = await window.reporterApi.getConfig()
    renderConfig('online')
    log('心跳成功', result)
  } catch (error) {
    renderConfig('error')
    log('心跳失败', getErrorMessage(error))
  } finally {
    setBusy(false)
  }
}

async function handleLogin() {
  try {
    setBusy(true)
    log('正在打开登录窗口')
    const result = await window.reporterApi.startLogin(providerSelect.value)
    state.credentials = result.credentials || {}
    credentialsInput.value = JSON.stringify(result.credentials, null, 2)
    renderCredentials()
    log('凭据提取完成', result)
  } catch (error) {
    log('登录/提取失败', getErrorMessage(error))
  } finally {
    setBusy(false)
  }
}

async function handleUpload() {
  try {
    setBusy(true)
    state.config = await window.reporterApi.saveConfig({ backendUrl: backendUrlInput.value })
    const credentials = parseCredentials()
    const result = await window.reporterApi.uploadAccount({
      providerId: providerSelect.value,
      name: accountNameInput.value || `${providerSelect.value} Account`,
      email: emailInput.value || undefined,
      credentials,
    })
    renderConfig('online')
    log('账号上传成功', result)
  } catch (error) {
    log('账号上传失败', getErrorMessage(error))
  } finally {
    setBusy(false)
  }
}

function renderConfig(status) {
  const config = state.config || {}
  backendUrlInput.value = config.backendUrl || 'http://localhost:8080'
  if (config.clientId) {
    clientTitle.textContent = '上报端已注册'
    clientInfo.textContent = `Client ID: ${config.clientId}`
    connectionBadge.textContent = status === 'error' ? '连接异常' : status === 'online' ? '在线' : '已注册'
    connectionBadge.className = `badge ${status === 'error' ? 'badge-warn' : 'badge-ok'}`
  } else {
    clientTitle.textContent = '尚未注册'
    clientInfo.textContent = '请先连接 Java 后端并注册上报端'
    connectionBadge.textContent = '未连接'
    connectionBadge.className = 'badge badge-muted'
  }
}

function renderProviders() {
  providerSelect.innerHTML = state.providers.map((provider) => `<option value="${escapeHtml(provider.id)}">${escapeHtml(provider.name)}</option>`).join('')
  updateProviderHint()
}

function renderCredentials() {
  const result = readCredentialsSafe()
  const keys = Object.keys(result.credentials)
  credentialBadge.textContent = result.valid ? (keys.length ? `${keys.length} 个字段` : '暂无凭据') : 'JSON 无效'
  credentialBadge.className = `badge ${result.valid ? (keys.length ? 'badge-ok' : 'badge-muted') : 'badge-warn'}`
}

function updateProviderHint() {
  const provider = state.providers.find((item) => item.id === providerSelect.value)
  providerHint.textContent = provider ? `${provider.name}：登录地址 ${provider.loginUrl}` : '当前仅支持已迁移 Provider。'
  if (!accountNameInput.value && provider) {
    accountNameInput.placeholder = `例如 ${provider.name} 主账号`
  }
}

function parseCredentials() {
  const input = credentialsInput.value.trim()
  const parsed = JSON.parse(input || '{}')
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('凭据必须是 JSON 对象')
  }
  state.credentials = parsed
  renderCredentials()
  return parsed
}

function readCredentialsSafe() {
  try {
    const input = credentialsInput.value.trim()
    const parsed = JSON.parse(input || '{}')
    return {
      valid: parsed && typeof parsed === 'object' && !Array.isArray(parsed),
      credentials: parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {},
    }
  } catch (_) {
    return { valid: false, credentials: state.credentials || {} }
  }
}

function setBusy(nextBusy) {
  state.busy = nextBusy
  const registered = Boolean(state.config?.clientId && state.config?.secret)
  registerBtn.disabled = nextBusy
  heartbeatBtn.disabled = nextBusy || !registered
  loginBtn.disabled = nextBusy
  uploadBtn.disabled = nextBusy || !registered
  registerBtn.textContent = nextBusy ? '处理中...' : '注册 / 更新上报端'
  heartbeatBtn.textContent = nextBusy ? '处理中...' : '心跳检测'
  loginBtn.textContent = nextBusy ? '处理中...' : '打开登录窗口并提取凭据'
  uploadBtn.textContent = nextBusy ? '处理中...' : '上传当前凭据'
}

function getErrorMessage(error) {
  return error instanceof Error ? error.message : String(error)
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (char) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  })[char])
}

registerBtn.addEventListener('click', handleRegister)
heartbeatBtn.addEventListener('click', handleHeartbeat)
loginBtn.addEventListener('click', handleLogin)
uploadBtn.addEventListener('click', handleUpload)
providerSelect.addEventListener('change', updateProviderHint)
credentialsInput.addEventListener('input', renderCredentials)
clearLogBtn.addEventListener('click', () => {
  logOutput.textContent = ''
})

window.reporterApi.onLog((message) => log(message))
init().catch((error) => log('初始化失败', getErrorMessage(error)))
