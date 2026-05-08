const providers = [
  { id: 'deepseek', name: 'DeepSeek', loginUrl: 'https://chat.deepseek.com', successPatterns: ['chat.deepseek.com'], localStorageKeys: ['userToken', 'token', 'accessToken'] },
  { id: 'glm', name: 'GLM', loginUrl: 'https://chatglm.cn', successPatterns: ['chatglm.cn'], localStorageKeys: ['refresh_token', 'token'] },
  { id: 'kimi', name: 'Kimi', loginUrl: 'https://www.kimi.com', successPatterns: ['kimi.com'], localStorageKeys: ['access_token', 'token'] },
  { id: 'qwen', name: 'Qwen', loginUrl: 'https://chat.qwen.ai', successPatterns: ['chat.qwen.ai'], localStorageKeys: ['token', 'accessToken', 'session'] },
  { id: 'qwen-ai', name: 'Qwen AI', loginUrl: 'https://chat.qwen.ai', successPatterns: ['chat.qwen.ai'], localStorageKeys: ['token', 'accessToken', 'session'] },
  { id: 'zai', name: 'Z.ai', loginUrl: 'https://chat.z.ai', successPatterns: ['chat.z.ai'], localStorageKeys: ['token', 'accessToken'] },
  { id: 'minimax', name: 'MiniMax', loginUrl: 'https://agent.minimaxi.com', successPatterns: ['minimaxi.com'], localStorageKeys: ['token', 'accessToken', 'jwt', 'realUserID'] },
  { id: 'mimo', name: 'Mimo', loginUrl: 'https://aistudio.xiaomimimo.com', successPatterns: ['xiaomimimo.com'], localStorageKeys: ['token'] },
  { id: 'perplexity', name: 'Perplexity', loginUrl: 'https://www.perplexity.ai', successPatterns: ['perplexity.ai'], localStorageKeys: ['token'] },
]

module.exports = { providers }
