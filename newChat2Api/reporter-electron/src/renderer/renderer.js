const backendUrlInput = document.getElementById('backendUrl')
const registerBtn = document.getElementById('registerBtn')
const heartbeatBtn = document.getElementById('heartbeatBtn')
const providerSelect = document.getElementById('provider')
const accountNameInput = document.getElementById('accountName')
const emailInput = document.getElementById('email')
const loginBtn = document.getElementById('loginBtn')
const uploadBtn = document.getElementById('uploadBtn')
const credentialsInput = document.getElementById('credentials')
const clientInfo = document.getElementById('clientInfo')
const logOutput = document.getElementById('log')

function log(message, payload) {
  const line = `[${new Date().toLocaleTimeString()}] ${message}${payload ? `\n${JSON.stringify(payload, null, 2)}` : ''}`
  logOutput.textContent = `${line}\n${logOutput.textContent}`
}

async function init() {
  const config = await window.reporterApi.getConfig()
  backendUrlInput.value = config.backendUrl
  clientInfo.textContent = config.clientId ? `Client ID: ${config.clientId}` : '尚未注册上报端'
  const providers = await window.reporterApi.providers()
  providerSelect.innerHTML = providers.map((provider) => `<option value="${provider.id}">${provider.name}</option>`).join('')
}

registerBtn.addEventListener('click', async () => {
  try {
    const config = await window.reporterApi.register({ backendUrl: backendUrlInput.value, name: 'Desktop Reporter' })
    clientInfo.textContent = `Client ID: ${config.clientId}`
    log('上报端注册成功')
  } catch (error) {
    log('注册失败', String(error))
  }
})

heartbeatBtn.addEventListener('click', async () => {
  try {
    await window.reporterApi.saveConfig({ backendUrl: backendUrlInput.value })
    const result = await window.reporterApi.heartbeat()
    log('心跳成功', result)
  } catch (error) {
    log('心跳失败', String(error))
  }
})

loginBtn.addEventListener('click', async () => {
  try {
    log('正在打开登录窗口')
    const result = await window.reporterApi.startLogin(providerSelect.value)
    credentialsInput.value = JSON.stringify(result.credentials, null, 2)
    log('凭据提取完成', result)
  } catch (error) {
    log('登录/提取失败', String(error))
  }
})

uploadBtn.addEventListener('click', async () => {
  try {
    await window.reporterApi.saveConfig({ backendUrl: backendUrlInput.value })
    const credentials = JSON.parse(credentialsInput.value || '{}')
    const result = await window.reporterApi.uploadAccount({
      providerId: providerSelect.value,
      name: accountNameInput.value || `${providerSelect.value} Account`,
      email: emailInput.value || undefined,
      credentials,
    })
    log('账号上传成功', result)
  } catch (error) {
    log('账号上传失败', String(error))
  }
})

window.reporterApi.onLog((message) => log(message))
init()
