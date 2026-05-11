# 上报端接口对接文档

本文档面向需要自定义实现上报端的开发者，说明如何向 Chat2API Backend 注册上报端、保持心跳、上传第三方 Provider 账号凭据。

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

本文后续示例统一使用：

```text
BASE_URL=http://服务器IP:8080
```

## 2. 上报端职责

自定义上报端通常需要完成以下流程：

1. 从管理后台获取一个启用状态的上报注册口令。
2. 调用 `/api/reporter/register` 注册上报端，换取 `clientId` 和 `secret`。
3. 本地安全保存 `clientId`、`secret` 和后端地址。
4. 定时调用 `/api/reporter/heartbeat` 保持在线状态。
5. 登录或采集第三方 Provider 账号凭据后，调用 `/api/reporter/accounts` 上传账号。

## 3. 通用约定

### 3.1 Content-Type

所有 Reporter 接口都使用 JSON：

```http
Content-Type: application/json
```

### 3.2 响应包装格式

成功响应统一格式：

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

失败响应统一格式：

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "BAD_REQUEST",
    "message": "错误信息"
  }
}
```

### 3.3 上报端认证 Header

除注册接口外，其余 Reporter 接口必须携带注册后得到的凭据：

```http
X-Reporter-Id: reporter_xxx
X-Reporter-Secret: rpt_xxx
```

`clientId` 和 `secret` 等同于上报端的长期凭据，必须保密保存。泄露后应在管理端禁用对应上报端，或重新注册生成新凭据。

### 3.4 安全和访问控制

`/api/reporter/**` 不需要管理员 JWT，但会使用上报端自己的 `X-Reporter-Id` 和 `X-Reporter-Secret` 鉴权。

当前安全配置允许跨域 Header：

```text
Authorization, Content-Type, X-API-Key, X-Reporter-Id, X-Reporter-Secret
```

Reporter 接口有频率限制，默认值为每个 IP、HTTP 方法、接口路径每 `60` 秒最多 `60` 次。可通过后端配置项调整：

```text
CHAT2API_RATE_LIMIT_WINDOW_SECONDS=60
CHAT2API_REPORTER_RATE_LIMIT=60
```

请求体大小默认限制为 `1048576` 字节，可通过后端配置项调整：

```text
CHAT2API_MAX_REQUEST_BODY_BYTES=1048576
```

## 4. 获取上报注册口令

上报注册口令由管理员在管理后台创建和启用。

进入管理后台后找到：

```text
上报注册口令 / Reporter Registration Codes
```

创建或复制一个已启用的口令，提供给自定义上报端使用。

口令要求：

| 项 | 规则 |
| --- | --- |
| 最小长度 | `8` 个字符 |
| 最大长度 | `128` 个字符 |
| 状态 | 必须是启用状态 |

注册失败时常见错误：

| HTTP 状态 | message | 含义 |
| --- | --- | --- |
| `401` | `Reporter registration code is required` | 未传注册口令 |
| `401` | `No enabled reporter registration code exists. Please create one in admin console first` | 后端没有启用的上报注册口令 |
| `401` | `Invalid reporter registration code. Please copy an enabled code from admin console` | 注册口令错误或已停用 |

## 5. 接口列表

| 接口 | 方法 | 是否需要上报端认证 | 说明 |
| --- | --- | --- | --- |
| `/api/reporter/register` | `POST` | 否 | 使用注册口令注册上报端，返回 `clientId` 和 `secret` |
| `/api/reporter/heartbeat` | `POST` | 是 | 心跳接口，刷新上报端在线状态 |
| `/api/reporter/accounts` | `POST` | 是 | 上传 Provider 账号和凭据 |

## 6. 注册上报端

### 6.1 请求

```http
POST /api/reporter/register
Content-Type: application/json
```

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `registrationCode` | string | 是 | 管理后台创建并启用的上报注册口令 |
| `name` | string | 否 | 上报端名称，默认 `Reporter Client` |
| `version` | string | 否 | 上报端版本，默认 `unknown` |

请求示例：

```bash
curl -X POST "$BASE_URL/api/reporter/register" \
  -H 'Content-Type: application/json' \
  -d '{
    "registrationCode": "rrc_xxx",
    "name": "Custom Reporter",
    "version": "1.0.0"
  }'
```

### 6.2 响应

返回示例：

```json
{
  "success": true,
  "data": {
    "clientId": "reporter_xxx",
    "secret": "rpt_xxx"
  },
  "error": null
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `data.clientId` | 上报端 ID，后续请求放入 `X-Reporter-Id` |
| `data.secret` | 上报端密钥，后续请求放入 `X-Reporter-Secret` |

注册成功后，后端会将上报端状态设为 `online`，并记录当前心跳时间。

## 7. 心跳接口

### 7.1 请求

```http
POST /api/reporter/heartbeat
Content-Type: application/json
X-Reporter-Id: reporter_xxx
X-Reporter-Secret: rpt_xxx
```

请求示例：

```bash
curl -X POST "$BASE_URL/api/reporter/heartbeat" \
  -H 'Content-Type: application/json' \
  -H 'X-Reporter-Id: reporter_xxx' \
  -H 'X-Reporter-Secret: rpt_xxx'
```

### 7.2 响应

返回示例：

```json
{
  "success": true,
  "data": {
    "status": "ok",
    "serverTime": "2026-05-11T09:00:00Z"
  },
  "error": null
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `data.status` | 固定为 `ok` |
| `data.serverTime` | 后端服务器时间，ISO-8601 格式 |

### 7.3 调用建议

建议自定义上报端启动后立即调用一次心跳，之后每 `30` 到 `60` 秒调用一次。

如果心跳返回 `401`，说明 `clientId` 或 `secret` 无效，应提示用户重新注册。

如果心跳返回 `403`，说明该上报端在后端被禁用，应停止上传账号并提示管理员处理。

## 8. 上传 Provider 账号

### 8.1 请求

```http
POST /api/reporter/accounts
Content-Type: application/json
X-Reporter-Id: reporter_xxx
X-Reporter-Secret: rpt_xxx
```

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `providerId` | string | 是 | Provider ID，必须在后端已存在 |
| `name` | string | 是 | 账号名称，不能为空 |
| `email` | string | 否 | 账号邮箱 |
| `credentials` | object | 否 | 账号凭据，键和值会按字符串保存 |
| `dailyLimit` | number | 否 | 该账号每日调用上限，传 `null` 或不传表示不限制 |

请求示例：

```bash
curl -X POST "$BASE_URL/api/reporter/accounts" \
  -H 'Content-Type: application/json' \
  -H 'X-Reporter-Id: reporter_xxx' \
  -H 'X-Reporter-Secret: rpt_xxx' \
  -d '{
    "providerId": "qwen-ai",
    "name": "张三的 Qwen 账号",
    "email": "user@example.com",
    "credentials": {
      "cookie": "token=xxx; session=yyy",
      "accessToken": "xxx"
    },
    "dailyLimit": 1000
  }'
```

### 8.2 响应

上传成功后，后端会立即创建账号、加密保存凭据，并执行一次账号校验，然后返回账号信息。

返回示例：

```json
{
  "success": true,
  "data": {
    "id": "acct_xxx",
    "providerId": "qwen-ai",
    "name": "张三的 Qwen 账号",
    "email": "user@example.com",
    "status": "ACTIVE",
    "errorMessage": null,
    "requestCount": 0,
    "todayUsed": 0,
    "dailyLimit": 1000,
    "lastUsed": "2026-05-11T09:00:00Z",
    "createdAt": "2026-05-11T09:00:00Z",
    "updatedAt": "2026-05-11T09:00:00Z"
  },
  "error": null
}
```

账号状态说明：

| status | 含义 |
| --- | --- |
| `ACTIVE` | 校验通过，账号可被代理服务使用 |
| `ERROR` | 校验失败，查看 `errorMessage` 获取原因 |
| `EXPIRED` | 账号过期 |
| `INACTIVE` | 账号停用 |

### 8.3 校验规则

上传账号后，后端会根据 Provider 配置校验基础字段：

| 校验项 | 失败时 errorMessage 可能包含 |
| --- | --- |
| 账号名称为空 | `account_name_missing` |
| Provider 不存在 | `provider_not_found` |
| Provider 未启用 | `provider_disabled` |
| Provider vendor 为空 | `provider_vendor_missing` |
| Provider API Endpoint 为空 | `provider_api_endpoint_missing` |
| 凭据为空 | `credentials_missing` |
| Cookie 类型 Provider 未提供 Cookie | `cookie_missing` |
| Token 或 JWT 类型 Provider 未提供 Token | `token_missing` |
| Qwen AI 未提供 Cookie 或 Token | `qwen_ai_cookie_or_token_missing` |
| Z.ai 未提供 Token | `zai_token_missing` |
| DeepSeek 未提供 Token | `deepseek_token_missing` |

`errorMessage` 中可能用逗号拼接多个错误，例如：

```text
credentials_missing,token_missing
```

## 9. Provider ID 和凭据字段

内置 Provider：

| providerId | 名称 | authType | 推荐 credentials 字段 |
| --- | --- | --- | --- |
| `zai` | Z.ai | `jwt` | `token` 或 `accessToken` |
| `deepseek` | DeepSeek | `userToken` | `token`、`userToken`、`accessToken` 或 `refreshToken` |
| `qwen-ai` | Qwen AI | `cookie` | `cookie` 或 `cookies`，也可同时上传 `token`、`accessToken` |

通用可识别 Token 字段：

```text
token, accessToken, access_token, apiKey, jwt, refresh_token, refreshToken
```

通用可识别 Cookie 字段：

```text
cookie, cookies
```

注意：

- `providerId` 必须和后端数据库中的 Provider ID 完全一致。
- `credentials` 会在后端加密保存，但上报端本地仍应避免明文落盘。
- 如果某个 Provider 后续调整了登录态字段，自定义上报端只需在 `credentials` 中增加对应字段，后端会以字符串 Map 保存。

## 10. 完整调用流程示例

### 10.1 JavaScript / TypeScript

```ts
const baseUrl = 'http://服务器IP:8080'

async function request(pathname: string, options: RequestInit = {}) {
  const response = await fetch(`${baseUrl}${pathname}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
  })
  const payload = await response.json()
  if (!response.ok || payload.success === false) {
    throw new Error(payload.error?.message || `Request failed: ${response.status}`)
  }
  return payload.data
}

async function register() {
  return request('/api/reporter/register', {
    method: 'POST',
    body: JSON.stringify({
      registrationCode: 'rrc_xxx',
      name: 'Custom Reporter',
      version: '1.0.0',
    }),
  })
}

async function heartbeat(clientId: string, secret: string) {
  return request('/api/reporter/heartbeat', {
    method: 'POST',
    headers: {
      'X-Reporter-Id': clientId,
      'X-Reporter-Secret': secret,
    },
  })
}

async function uploadAccount(clientId: string, secret: string) {
  return request('/api/reporter/accounts', {
    method: 'POST',
    headers: {
      'X-Reporter-Id': clientId,
      'X-Reporter-Secret': secret,
    },
    body: JSON.stringify({
      providerId: 'deepseek',
      name: 'DeepSeek Account',
      credentials: {
        token: 'user-token-value',
      },
    }),
  })
}
```

### 10.2 Python

```python
import requests

BASE_URL = "http://服务器IP:8080"


def unwrap(response):
    payload = response.json()
    if not response.ok or payload.get("success") is False:
        message = payload.get("error", {}).get("message") or f"Request failed: {response.status_code}"
        raise RuntimeError(message)
    return payload.get("data")


def register():
    response = requests.post(
        f"{BASE_URL}/api/reporter/register",
        json={
            "registrationCode": "rrc_xxx",
            "name": "Custom Reporter",
            "version": "1.0.0",
        },
        timeout=15,
    )
    return unwrap(response)


def reporter_headers(client_id, secret):
    return {
        "X-Reporter-Id": client_id,
        "X-Reporter-Secret": secret,
    }


def heartbeat(client_id, secret):
    response = requests.post(
        f"{BASE_URL}/api/reporter/heartbeat",
        headers=reporter_headers(client_id, secret),
        timeout=15,
    )
    return unwrap(response)


def upload_account(client_id, secret):
    response = requests.post(
        f"{BASE_URL}/api/reporter/accounts",
        headers=reporter_headers(client_id, secret),
        json={
            "providerId": "qwen-ai",
            "name": "Qwen Account",
            "credentials": {
                "cookie": "token=xxx; session=yyy",
            },
        },
        timeout=15,
    )
    return unwrap(response)
```

## 11. 错误码和排查

### 11.1 400 BAD_REQUEST

返回示例：

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "BAD_REQUEST",
    "message": "Account name is required"
  }
}
```

常见原因：

- `providerId` 不存在。
- `name` 为空。
- `dailyLimit` 无法转换为数字。
- 请求 JSON 字段类型不符合预期。

### 11.2 401 HTTP_ERROR

返回示例：

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "HTTP_ERROR",
    "message": "Invalid reporter credentials"
  }
}
```

常见原因：

- 未携带 `X-Reporter-Id`。
- 未携带 `X-Reporter-Secret`。
- `clientId` 或 `secret` 写错。
- 后端数据库中不存在对应上报端。
- 注册口令为空、错误或没有启用的注册口令。

### 11.3 403 HTTP_ERROR

返回示例：

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "HTTP_ERROR",
    "message": "Reporter client is disabled"
  }
}
```

常见原因：

- 上报端在后端被禁用。
- 上报端状态不是 `online` 或 `offline`。

### 11.4 429 RATE_LIMITED

返回示例：

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "RATE_LIMITED",
    "message": "Too many requests"
  }
}
```

常见原因：

- 同一 IP 在一个限流窗口内调用 Reporter 接口过于频繁。
- 心跳间隔过短。
- 上传端重试没有退避策略。

建议使用指数退避重试，例如 `5s`、`15s`、`30s`、`60s`。

### 11.5 500 INTERNAL_ERROR

返回示例：

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INTERNAL_ERROR",
    "message": "Failed to save credentials"
  }
}
```

常见原因：

- 后端数据库异常。
- 加密服务异常。
- 请求体触发了未预期的服务端错误。

## 12. 自定义上报端实现建议

- **持久化凭据**：注册成功后保存 `backendUrl`、`clientId`、`secret`，避免每次启动重复注册。
- **注册口令缓存**：可保存用户输入的 `registrationCode`，便于重新注册，但不要上传到非可信位置。
- **心跳状态机**：启动后先心跳，心跳成功后再允许上传账号。
- **失败重试**：网络错误和 `429` 可重试，`401` 应重新注册，`403` 应停止上传并提示管理员。
- **凭据最小化**：只上传后端转发所需的 Cookie、Token、JWT 等字段。
- **账号名称必填**：无法自动识别名称时，应要求用户手动填写。
- **HTTPS 优先**：生产环境建议通过 Nginx 或网关提供 HTTPS。
- **本地安全存储**：桌面端可使用系统 Keychain、Credential Manager 或加密文件保存上报端凭据。

## 13. 快速接入清单

1. 管理员在管理后台创建并启用上报注册口令。
2. 自定义上报端填写后端地址和注册口令。
3. 调用 `/api/reporter/register` 获取 `clientId` 和 `secret`。
4. 本地安全保存 `clientId` 和 `secret`。
5. 定时调用 `/api/reporter/heartbeat`。
6. 获取第三方 Provider 登录凭据。
7. 调用 `/api/reporter/accounts` 上传账号。
8. 检查返回账号的 `status` 和 `errorMessage`。
