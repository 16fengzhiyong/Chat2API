const providers = [
  { id: 'zai', name: 'Z.ai', loginUrl: 'https://chat.z.ai', successPatterns: ['chat.z.ai'], localStorageKeys: ['token', 'accessToken'] },
  { id: 'deepseek', name: 'DeepSeek', loginUrl: 'https://chat.deepseek.com', successPatterns: ['chat.deepseek.com'], localStorageKeys: ['userToken', 'token', 'accessToken', 'refreshToken'] },
  { id: 'qwen-ai', name: 'Qwen AI', loginUrl: 'https://chat.qwen.ai', successPatterns: ['chat.qwen.ai'], localStorageKeys: ['token', 'accessToken', 'session'] },
]

module.exports = { providers }
