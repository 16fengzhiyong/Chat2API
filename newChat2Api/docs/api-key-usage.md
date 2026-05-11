# API Key 使用说明

本文档只说明调用方拿到 API Key 后，如何接入 Chat2API。

## 1. 你需要准备什么

你需要从管理员那里拿到两项信息：

- **Base URL**：接口地址，例如 `http://127.0.0.1:8080/v1` 或 `https://你的域名/v1`
- **API Key**：密钥，例如 `c2a_xxx`

## 2. 在 OpenAI 兼容客户端中填写

大多数 OpenAI 兼容客户端只需要填写：

```text
Base URL: http://127.0.0.1:8080/v1
API Key: c2a_xxx
```

如果你的服务通过域名访问：

```text
Base URL: https://你的域名/v1
API Key: c2a_xxx
```

注意：`Base URL` 末尾通常要带 `/v1`。

## 3. 使用 curl 调用

### 普通对话请求

```bash
curl -X POST 'http://127.0.0.1:8080/v1/chat/completions' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer c2a_xxx' \
  -d '{
    "model": "模型名称",
    "messages": [
      {
        "role": "user",
        "content": "你好"
      }
    ]
  }'
```

把下面两处替换成实际值：

- **`http://127.0.0.1:8080`**：替换成你的服务地址
- **`c2a_xxx`**：替换成你的 API Key
- **`模型名称`**：替换成管理员提供或 `/v1/models` 返回的模型名

### 流式对话请求

```bash
curl -N -X POST 'http://127.0.0.1:8080/v1/chat/completions' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer c2a_xxx' \
  -d '{
    "model": "模型名称",
    "stream": true,
    "messages": [
      {
        "role": "user",
        "content": "请写一段简短介绍"
      }
    ]
  }'
```

## 4. Qwen 思考模式参数

当管理员提供的是 Qwen AI 模型时，请求体可以携带 `enable_thinking` 控制本次请求是否使用思考模式：

- **`enable_thinking: true`**：使用思考模式
- **`enable_thinking: false`**：使用快速模式
- **不传 `enable_thinking`**：使用管理平台配置的 Qwen 默认思考模式

示例：

```bash
curl -X POST 'http://127.0.0.1:8080/v1/chat/completions' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer c2a_xxx' \
  -d '{
    "model": "模型名称",
    "enable_thinking": true,
    "messages": [
      {
        "role": "user",
        "content": "请先思考再回答"
      }
    ]
  }'
```

`enable_thinking` 只影响本次请求，不会修改管理平台默认配置。

## 5. 查询可用模型

```bash
curl 'http://127.0.0.1:8080/v1/models'
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

调用时使用 `data[].id` 作为 `model`。

## 6. JavaScript 示例

```ts
import OpenAI from 'openai'

const client = new OpenAI({
  baseURL: 'http://127.0.0.1:8080/v1',
  apiKey: 'c2a_xxx',
})

const response = await client.chat.completions.create({
  model: '模型名称',
  messages: [
    { role: 'user', content: '你好' },
  ],
})

console.log(response.choices[0]?.message?.content)
```

## 7. Python 示例

```python
from openai import OpenAI

client = OpenAI(
    base_url="http://127.0.0.1:8080/v1",
    api_key="c2a_xxx",
)

response = client.chat.completions.create(
    model="模型名称",
    messages=[
        {"role": "user", "content": "你好"},
    ],
)

print(response.choices[0].message.content)
```

## 8. API Key 放在哪里

推荐放在请求头里：

```http
Authorization: Bearer c2a_xxx
```

也支持下面这种写法：

```http
X-API-Key: c2a_xxx
```

不推荐把 Key 放到 URL 参数里。

## 9. 常见错误

### 401 Invalid API key

说明 API Key 无效。

可能原因：

- 没有传 API Key
- API Key 写错了
- API Key 已被禁用
- 请求头格式不对

正确格式是：

```http
Authorization: Bearer c2a_xxx
```

### 403 API key is not allowed to use model

说明这个 API Key 没有权限使用当前模型。

处理方式：

- 换一个允许的 `model`
- 联系管理员给这个 Key 增加模型权限

### 404 Model not found

说明模型名不存在。

处理方式：

- 调用 `/v1/models` 查询可用模型
- 检查请求里的 `model` 是否拼错

## 10. 最小接入示例

```bash
curl -X POST 'http://127.0.0.1:8080/v1/chat/completions' \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer c2a_xxx' \
  -d '{
    "model": "模型名称",
    "messages": [{"role": "user", "content": "你好"}]
  }'
```
