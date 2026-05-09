const fs = require('fs')
const path = require('path')

function createConfigStore(app) {
  let configFile = ''
  let configData = {}

  function load() {
    configFile = path.join(app.getPath('userData'), 'reporter-config.json')
    try {
      configData = JSON.parse(fs.readFileSync(configFile, 'utf8'))
    } catch (_) {
      configData = {}
    }
    return get()
  }

  function get() {
    return {
      backendUrl: configData.backendUrl || 'http://localhost:8080',
      clientId: configData.clientId || '',
      secret: configData.secret || '',
    }
  }

  function save(next) {
    Object.entries(next || {}).forEach(([key, value]) => {
      if (value !== undefined) {
        configData[key] = value
      }
    })
    fs.mkdirSync(path.dirname(configFile), { recursive: true })
    fs.writeFileSync(configFile, JSON.stringify(configData, null, 2))
    return get()
  }

  return { load, get, save }
}

module.exports = { createConfigStore }
