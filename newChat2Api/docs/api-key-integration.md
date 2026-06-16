# API Key 接入文档

本文档面向需要通过 OpenAI 兼容协议接入 Chat2API Backend 的调用方，说明如何创建 API Key、如何携带 Key 调用接口，以及常见错误含义。

## 1. 接入地址

后端默认监听端口为 `8080`。

如果直接访问后端：

```text
http://服务器IP:8080
```

如果前面有 Nginx 或域名代理，请使用你的公网访问地址，例如：

```text
https://api.example.com
```

OpenAI 兼容客户端通常需要配置：

```text
Base URL: http://服务器IP:8080/v1
API Key: 后台创建的 API Key
```

## 2. 支持的 OpenAI 兼容接口

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/v1/chat/completions` | `POST` | 聊天补全接口，支持普通响应和流式响应 |
| `/v1/completions` | `POST` | 旧版文本补全接口，后端会转换为聊天请求处理 |
| `/v1/models` | `GET` | 获取当前启用 Provider 支持的模型列表 |
| `/v1/models/{model}` | `GET` | 查询指定模型是否存在 |

注意：API Key 校验逻辑当前作用在 `/v1/chat/completions` 和 `/v1/completions`。

## 3. 管理员启用 API Key 认证

API Key 认证由配置项 `apiKeyEnabled` 控制。

生产环境建议开启，否则 `/v1/chat/completions` 和 `/v1/completions` 不需要 API Key 也可以调用。

### 3.1 通过管理后台启用

登录 Admin Web 后进入：

```text
API密钥
```

打开全局 API Key 认证开关。

### 3.2 通过管理 API 启用

先登录获取管理员 JWT：

```bash
curl -X POST 'http://服务器IP:8080/api/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "admin",
    "password": "admin123456"
  }'
```

返回示例：

```json
{
  "success": true,
  "data": {
    "token": "管理员JWT",
    "tokenType": "Bearer",
    "expiresAt": "2026-05-12T00:00:00Z",
    "user": {
      "username": "admin",
      "roles": ["ADMIN"]
    }
  },
  "error": null
}
```

启用 API Key 认证：

```bash
curl -X POST 'http://服务器IP:8080/api/config' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer 管理员JWT' \
  -d '{
    "key": "apiKeyEnabled",
    "value": "true"
  }'
```

关闭 API Key 认证时，将 `value` 改为 `false`。

## 4. 创建 API Key

### 4.1 通过管理后台创建

登录 Admin Web，进入：

```text
API密钥 -> 新建密钥
```

可以设置：

- **名称**：用于区分调用方，例如 `Cherry Studio`、`Kilo Code`、`测试环境`。
- **描述**：可选。
- **模型权限**：可以允许全部模型，或只允许指定模型。

创建后会得到一个类似 `c2a...` 开头的 Key。调用方需要保存这个 Key。

### 4.2 通过管理 API 创建

创建不限制模型的 API Key：

```bash
curl -X POST 'http://服务器IP:8080/api/api-keys' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer 管理员JWT' \
  -d '{
    "name": "client-demo",
    "description": "demo client",
    "allowedModels": []
  }'
```

创建只允许部分模型的 API Key：

```bash
curl -X POST 'http://服务器IP:8080/api/api-keys' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer 管理员JWT' \
  -d '{
    "name": "qwen-client",
    "description": "only qwen models",
    "allowedModels": ["qwen-plus", "qwen-max"]
  }'
```

返回示例：

```json
{
  "success": true,
  "data": {
    "id": "key_xxx",
    "name": "client-demo",
    "keyValue": "c2a_xxx",
    "enabled": true,
    "usageCount": 0,
    "description": "demo client",
    "allowedModels": [],
    "createdAt": "2026-05-11T03:00:00Z",
    "lastUsedAt": null
  },
  "error": null
}
```

调用方真正使用的是 `data.keyValue`。

## 5. 模型权限规则

API Key 的模型权限字段为 `allowedModels`。

| `allowedModels` 值 | 含义 |
| --- | --- |
| `[]` | 不限制模型，允许使用全部模型 |
| `null` 或未传 | 不限制模型，允许使用全部模型 |
| `["all"]` | 创建或更新时会按不限制模型处理 |
| `["qwen-plus"]` | 只允许使用 `qwen-plus` |
| `["qwen*"]` | 允许使用以 `qwen` 开头的模型 |

校验规则：

- 模型名精确匹配时允许调用。
- 权限项以 `*` 结尾时，按前缀匹配。
- 匹配时忽略大小写。
- 请求体里的 `model` 为空时，如果该 Key 配置了模型限制，会被拒绝。

如果模型不在 Key 的允许范围内，后端返回 `403`。

## 6. 调用方式

推荐使用 `Authorization: Bearer` 携带 API Key。

### 6.1 Chat Completions

```bash
curl -X POST 'http://服务器IP:8080/v1/chat/completions' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer c2a_xxx' \
  -d '{
    "model": "qwen-plus",
    "messages": [
      {
        "role": "user",
        "content": "你好，介绍一下你自己"
      }
    ]
  }'
```

### 6.2 流式响应

```bash
curl -N -X POST 'http://服务器IP:8080/v1/chat/completions' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer c2a_xxx' \
  -d '{
    "model": "qwen-plus",
    "stream": true,
    "messages": [
      {
        "role": "user",
        "content": "写一段 100 字的介绍"
      }
    ]
  }'
```

流式响应的 `Content-Type` 为：

```text
text/event-stream; charset=utf-8
```

### 6.3 Qwen 思考模式参数

当请求路由到 Qwen AI Provider 时，可以在请求体中携带 `enable_thinking` 控制是否使用思考模式：

| 参数 | 含义 |
| --- | --- |
| `enable_thinking: true` | 使用 Qwen 思考模式 |
| `enable_thinking: false` | 使用 Qwen 快速模式 |
| 未携带 `enable_thinking` | 使用管理平台中 Qwen 设置的默认思考模式 |

示例：

```bash
curl -X POST 'http://服务器IP:8080/v1/chat/completions' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer c2a_xxx' \
  -d '{
    "model": "qwen-plus",
    "enable_thinking": true,
    "messages": [
      {
        "role": "user",
        "content": "请推理并解释这个问题"
      }
    ]
  }'
```

注意：`enable_thinking` 只覆盖本次请求的思考/快速模式，不会修改管理平台默认配置；Qwen 的自动模式由管理平台默认配置控制。

### 6.4 Qwen 搜索模式参数

当请求路由到 Qwen AI Provider 时，可以在请求体中携带 `enable_search` 控制是否使用 Qwen 搜索模式：

| 参数 | 含义 |
| --- | --- |
| `enable_search: true` | 使用 Qwen 搜索模式 |
| `enable_search: false` | 使用 Qwen 普通对话模式 |
| `search: true` | 等价于 `enable_search: true` |
| 模型名带 `-search` 后缀 | 使用 Qwen 搜索模式，例如 `Qwen3.6-Plus-search` |
| 未携带搜索参数 | 使用管理平台中 Qwen 设置的默认搜索模式 |

示例：

```bash
curl -X POST 'http://服务器IP:8080/v1/chat/completions' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer c2a_xxx' \
  -d '{
    "model": "qwen-plus",
    "enable_search": true,
    "messages": [
      {
        "role": "user",
        "content": "搜索并总结今天的 AI 新闻"
      }
    ]
  }'
```

注意：`enable_search` 只覆盖本次请求的搜索/普通模式，不会修改管理平台默认配置；带 `-search` 后缀的模型名会在转发到 Qwen 前自动还原为真实模型名。

### 6.5 Completions

```bash
curl -X POST 'http://服务器IP:8080/v1/completions' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer c2a_xxx' \
  -d '{
    "model": "qwen-plus",
    "prompt": "你好，介绍一下你自己"
  }'
```

后端会把 `prompt` 转成一条 `user` 消息后复用聊天补全流程。

### 6.6 查询模型列表

```bash
curl 'http://服务器IP:8080/v1/models'
```

返回示例：

```json
{
  "object": "list",
  "data": [
    {
      "id": "qwen-plus",
      "object": "model",
      "created": 0,
      "owned_by": "chat2api"
    }
  ]
}
```

## 7. 其他携带 Key 的方式

后端支持三种 API Key 传递方式。

### 7.1 Authorization Bearer，推荐

```http
Authorization: Bearer c2a_xxx
```

### 7.2 X-API-Key Header

```http
X-API-Key: c2a_xxx
```

### 7.3 查询参数，不推荐生产使用

```text
/v1/chat/completions?api_key=c2a_xxx
```

查询参数容易出现在网关日志、浏览器历史、访问日志中，生产环境建议避免使用。

## 8. OpenAI SDK 示例

### 8.1 JavaScript / TypeScript

```ts
import OpenAI from 'openai'

const client = new OpenAI({
  baseURL: 'http://服务器IP:8080/v1',
  apiKey: 'c2a_xxx',
})

const response = await client.chat.completions.create({
  model: 'qwen-plus',
  messages: [
    { role: 'user', content: '你好' },
  ],
})

console.log(response.choices[0]?.message?.content)
```

### 8.2 Python

```python
from openai import OpenAI

client = OpenAI(
    base_url="http://服务器IP:8080/v1",
    api_key="c2a_xxx",
)

response = client.chat.completions.create(
    model="qwen-plus",
    messages=[
        {"role": "user", "content": "你好"},
    ],
)

print(response.choices[0].message.content)
```

## 9. 常见错误

### 9.1 401 Invalid API key

返回示例：

```json
{
  "error": {
    "message": "Invalid API key",
    "type": "chat2api_error"
  }
}
```

可能原因：

- 未携带 API Key。
- API Key 写错。
- API Key 已被禁用。
- API Key 不存在。
- 请求头格式错误，例如漏写 `Bearer`。

### 9.2 403 API key is not allowed to use model

返回示例：

```json
{
  "error": {
    "message": "API key is not allowed to use model: qwen-max",
    "type": "chat2api_error"
  }
}
```

可能原因：

- 当前 API Key 配置了模型限制。
- 请求体中的 `model` 不在 `allowedModels` 中。
- 请求体缺少 `model` 字段。

### 9.3 404 Model not found

访问 `/v1/models/{model}` 时，如果模型不存在，会返回：

```json
{
  "error": {
    "message": "Model not found",
    "type": "invalid_request_error"
  }
}
```

### 9.4 HTTP request header 解析错误

如果日志中出现类似：

```text
Invalid character found in method name
```

通常是有人用非 HTTP 协议访问了后端 HTTP 端口，例如把 `http://服务器IP:8080` 误写成 `https://服务器IP:8080`，或公网扫描器访问了端口。

## 10. 安全建议

- **生产环境开启 API Key 认证**：确保 `apiKeyEnabled=true`。
- **优先使用 HTTPS**：可以通过 Nginx 做 TLS，再转发到后端 HTTP 端口。
- **不要把 API Key 写进前端公开代码**：浏览器前端会暴露 Key。
- **不要使用查询参数传 Key**：除非只是临时测试。
- **按调用方拆分 Key**：不同用户、应用、环境使用不同 API Key。
- **使用模型权限隔离能力**：只给调用方开放需要的模型。
- **泄露后立即禁用或删除 Key**：管理后台可以禁用、删除 API Key。
- **不要直接暴露管理接口**：`/api/**` 管理接口需要管理员 JWT，生产环境建议只允许内网或管理端访问。

## 11. 快速接入清单

1. 管理员登录 Admin Web。
2. 在 `API密钥` 页面开启全局 API Key 认证。
3. 创建一个 API Key。
4. 把 `Base URL` 配置为 `http://服务器IP:8080/v1` 或你的域名 `/v1`。
5. 把 `API Key` 配置为创建出来的 `c2a_xxx`。
6. 使用 `/v1/chat/completions` 发起 OpenAI 兼容请求。
