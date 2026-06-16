# Chat2API 一般接入文档（likegpt 域名示例）

本文档面向使用 OpenAI 兼容客户端或直接调用 HTTP 的接入方，结合现网 Nginx 部署，提供以 likegpt 域名为例的统一接入说明，并补充 Qwen 的思考/搜索/连续会话等能力参数。

- 示例域名（生产）：`https://www.likegpt.top`
- Base URL：`https://www.likegpt.top/v1`
- 管理后台与同域：静态资源同域，管理接口走 `/api/`；OpenAI 兼容接口走 `/v1/`

更多基础用法可参考 `docs/api-key-usage.md`。

## 1. 你需要准备什么

- Base URL：`https://www.likegpt.top/v1`
- API Key：例如 `c2a_xxx`（由管理员发放）

## 2. OpenAI 兼容客户端填写

```
Base URL: https://www.likegpt.top/v1
API Key: c2a_xxx
```

常见客户端（OpenAI 官方 SDK、NextChat、One API、Chatbox、Cursor 等）均支持上述两项即可使用。

## 3. 基本调用示例

- 非流式

```bash
curl -X POST 'https://www.likegpt.top/v1/chat/completions' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer c2a_xxx' \
  -d '{
    "model": "模型名称",
    "messages": [
      {"role": "user", "content": "你好"}
    ]
  }'
```

- 流式（SSE）

```bash
curl -N -X POST 'https://www.likegpt.top/v1/chat/completions' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer c2a_xxx' \
  -d '{
    "model": "模型名称",
    "stream": true,
    "messages": [
      {"role": "user", "content": "请写一段简短介绍"}
    ]
  }'
```

- 查询可用模型

```bash
curl 'https://www.likegpt.top/v1/models' -H 'Authorization: Bearer c2a_xxx'
```

## 4. JavaScript / Python SDK 示例

- JavaScript（openai@4）

```ts
import OpenAI from 'openai'

const client = new OpenAI({
  baseURL: 'https://www.likegpt.top/v1',
  apiKey: 'c2a_xxx',
})

const resp = await client.chat.completions.create({
  model: '模型名称',
  messages: [{ role: 'user', content: '你好' }],
})

console.log(resp.choices[0]?.message?.content)
```

- Python（openai>=1.x）

```python
from openai import OpenAI

client = OpenAI(
    base_url="https://www.likegpt.top/v1",
    api_key="c2a_xxx",
)

resp = client.chat.completions.create(
    model="模型名称",
    messages=[{"role": "user", "content": "你好"}],
)

print(resp.choices[0].message.content)
```

## 5. Qwen 专属参数与模式

Qwen 在 Chat2API 中支持“思考模式”“搜索模式”“连续会话（记录模式）”。主要通过请求体参数进行控制。

- 模型名后缀快捷方式（不区分大小写）：
  - `-thinking`：思考模式
  - `-fast`：快速模式

- 管理平台可配置的 Qwen 全局默认：
  - 思考模式默认值（auto/thinking/fast）
  - 搜索模式默认值（auto/off/search）
  - 记录模式（record/local）用于是否复用上次会话

### 5.1 思考模式（Thinking）

- 覆盖参数：`enable_thinking: true|false`
- 其他触发条件：
  - 模型名包含 `-thinking` 或包含 `think`/`r1`
  - `reasoning_effort` 传入且不为 `false` 时也视为开启思考
- 未显式传参时，走管理平台默认思考模式

示例：

```json
{
  "model": "qwen-plus-thinking",
  "messages": [{"role":"user","content":"请先思考再回答"}]
}
```

或：

```json
{
  "model": "qwen-plus",
  "enable_thinking": true,
  "messages": [{"role":"user","content":"请先思考再回答"}]
}
```

- 流式返回会在 `choices[].delta.reasoning_content` 输出思考内容增量；
- 非流式返回会在 `message.reasoning_content` 给出最终思考结果（若存在）。

可选：`thinking_budget` 可透传给上游用于控制思考预算。

### 5.2 搜索模式（Search / Web Research）

- 覆盖参数（两者等价，择一即可）：
  - `enable_search: true|false`
  - `search: true|false|"search"|"on"|"yes"|"1"`
- 未显式传参时，走管理平台默认搜索模式（auto/off/search）。当默认为 `search` 时自动开启

示例：

```json
{
  "model": "qwen-plus",
  "enable_search": true,
  "messages": [{"role":"user","content":"帮我调研下最近的行业新闻"}]
}
```


### 5.3 连续会话（记录模式 Record / Local）

- 管理平台“记录模式”用于是否复用 Qwen 的 `chat_id` 与上一轮的 `parent_id`，从而实现真正的连续对话
- 客户端侧如需续接同一会话，请在请求体携带稳定的 `sessionId`：

```json
{
  "model": "qwen-plus",
  "sessionId": "your-stable-session-id",
  "messages": [{"role":"user","content":"我们继续上次的话题"}]
}
```

- 注意：`sessionId` 仅在“记录模式”为 record（正常存档）时生效；当为 local 时不会持久化，不会复用历史

## 6. 认证与请求头

- 推荐：`Authorization: Bearer c2a_xxx`
- 兼容：`X-API-Key: c2a_xxx`

## 7. 常见错误与排查

- 401 Invalid API key：Key 缺失/错误/被禁用，或请求头格式不对（确认 `Authorization: Bearer c2a_xxx`）
- 403 API key is not allowed to use model：当前 Key 无权使用该模型；请更换模型或联系管理员授权
- 404 Model not found：模型名不存在；请先 `GET /v1/models` 并以返回的 `data[].id` 作为 `model`
- 429/超时：请重试或降低并发。现网 Nginx 对 `/v1/` 设置了限流与长连接保持，流式超时上限约 1 小时

## 8. 与现网 Nginx 的兼容性说明

- likegpt 域名（`www.likegpt.top`）对外提供：
  - `/v1/`：OpenAI 兼容接口（已禁用代理缓冲，适配 SSE）
  - `/api/`：管理后台接口（需管理员登录）
- SSE 已配置 `X-Accel-Buffering: no` 与长时超时，流式输出可稳定透传

## 9. 最小接入清单

- Base URL：`https://www.likegpt.top/v1`
- API Key：`c2a_xxx`
- 模型名：请以 `/v1/models` 返回的 `data[].id` 为准
- Qwen 进阶：
  - 思考：`enable_thinking: true|false` 或模型后缀 `-thinking/-fast`
  - 搜索：`enable_search: true|false` 或 `search: true|false|"search"`
  - 连续会话：在记录模式开启时传入稳定 `sessionId`
