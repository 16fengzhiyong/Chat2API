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
  connectionStatus: 'unregistered',
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
    verifyRegistrationStatus()
    startHeartbeatTimer()
  }
}

let heartbeatTimer = null

function startHeartbeatTimer() {
  if (heartbeatTimer) clearInterval(heartbeatTimer)
  heartbeatTimer = setInterval(() => verifyRegistrationStatus('自动心跳'), 5 * 60 * 1000)
}

async function verifyRegistrationStatus(label = '注册状态确认') {
  const config = state.config || {}
  if (!config.clientId || !config.secret) {
    renderConfig('unregistered')
    return
  }
  try {
    const result = await window.reporterApi.heartbeat()
    state.config = await window.reporterApi.getConfig()
    renderConfig('online')
    log(`${label}成功`, result)
  } catch (error) {
    state.config = await window.reporterApi.getConfig()
    renderConfig('error')
    log(`${label}失败`, getErrorMessage(error))
  }
}

async function handleRegister() {
  try {
    const registrationCode = registrationCodeInput.value.trim()
    if (!registrationCode) {
      registrationCodeInput.focus()
      throw new Error('请先在管理端创建并复制启用的上报注册口令，然后填写到注册口令')
    }
    setBusy(true)
    const config = await window.reporterApi.register({ backendUrl: backendUrlInput.value.trim(), name: 'Desktop Reporter', registrationCode })
    state.config = config
    renderConfig('online')
    log('上报端注册成功')
    startHeartbeatTimer()
  } catch (error) {
    state.config = await window.reporterApi.getConfig()
    renderConfig(state.config?.clientId && state.config?.secret ? 'error' : 'unregistered')
    log('注册失败', getErrorMessage(error))
  } finally {
    setBusy(false)
  }
}

async function handleHeartbeat() {
  try {
    setBusy(true)
    state.config = await window.reporterApi.saveConfig({ backendUrl: backendUrlInput.value, registrationCode: registrationCodeInput.value })
    await verifyRegistrationStatus('心跳检测')
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
    if (!accountNameInput.value.trim() && result.account?.name) {
      accountNameInput.value = result.account.name
    }
    if (!emailInput.value.trim() && result.account?.email) {
      emailInput.value = result.account.email
    }
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
    state.config = await window.reporterApi.saveConfig({ backendUrl: backendUrlInput.value, registrationCode: registrationCodeInput.value })
    const credentials = parseCredentials()
    const accountName = accountNameInput.value.trim()
    if (!accountName) {
      accountNameInput.focus()
      throw new Error('请填写账号名称后再上传')
    }
    const result = await window.reporterApi.uploadAccount({
      providerId: providerSelect.value,
      name: accountName,
      email: emailInput.value.trim() || undefined,
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
  if (status) {
    state.connectionStatus = status
  }
  if (document.activeElement !== backendUrlInput) {
    backendUrlInput.value = config.backendUrl || 'http://localhost:8080'
  }
  if (document.activeElement !== registrationCodeInput) {
    registrationCodeInput.value = config.registrationCode || ''
  }
  const registered = Boolean(config.clientId && config.secret)
  if (registered) {
    const currentStatus = state.connectionStatus
    clientTitle.textContent = currentStatus === 'online' ? '上报端已注册' : currentStatus === 'error' ? '上报端连接异常' : '注册信息已缓存'
    clientInfo.textContent = `Client ID: ${config.clientId}`
    connectionBadge.textContent = currentStatus === 'online' ? '在线' : currentStatus === 'error' ? '需重新检测' : '待确认'
    connectionBadge.className = `badge ${currentStatus === 'online' ? 'badge-ok' : 'badge-warn'}`
  } else {
    state.connectionStatus = 'unregistered'
    clientTitle.textContent = '尚未注册'
    clientInfo.textContent = '请先连接 Java 后端并注册上报端'
    connectionBadge.textContent = '未连接'
    connectionBadge.className = 'badge badge-muted'
  }
  setBusy(state.busy)
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
  const online = Boolean(state.config?.clientId && state.config?.secret && state.connectionStatus === 'online')
  registerBtn.disabled = nextBusy
  heartbeatBtn.disabled = nextBusy || !state.config?.clientId || !state.config?.secret
  loginBtn.disabled = nextBusy
  uploadBtn.disabled = nextBusy || !online
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
