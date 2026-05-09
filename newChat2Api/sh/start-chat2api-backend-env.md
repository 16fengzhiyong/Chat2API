# Chat2API Backend 启动环境变量说明

本文档说明 `start-chat2api-backend.sh` 中配置的临时环境变量作用。

这些环境变量会覆盖 `backend-java/src/main/resources/application.yml` 中的默认配置，只对通过该脚本启动的后端进程及其子进程生效。

## 服务配置

### `CHAT2API_SERVER_PORT`

后端服务监听端口。

当前默认值：`8080`

启动后访问地址通常为：`http://服务器IP:8080`

## 数据库配置

### `CHAT2API_DB_URL`

MySQL 数据库连接地址。

当前默认值：

```text
jdbc:mysql://127.0.0.1:3306/chat2api?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
```

默认含义：

- 数据库地址：`127.0.0.1`
- 数据库端口：`3306`
- 数据库名称：`chat2api`
- 时区：`Asia/Shanghai`

### `CHAT2API_DB_USERNAME`

MySQL 数据库用户名。

当前默认值：`root`

### `CHAT2API_DB_PASSWORD`

MySQL 数据库密码。

当前默认值为空，请通过环境变量提供生产数据库密码。

## 数据安全配置

### `CHAT2API_ENCRYPTION_KEY`

用于加密数据库中保存的敏感账号凭据，例如 Provider 账号的 token、cookie 等。

当前默认值：`dev-only-change-this-secret-key`

注意事项：

- 生产环境必须固定且保密。
- 系统保存账号凭据后，不建议再修改。
- 修改后，旧数据可能无法解密。

## 管理后台登录配置

### `CHAT2API_ADMIN_USERNAME`

管理后台登录用户名。

当前默认值：`admin`

### `CHAT2API_ADMIN_PASSWORD`

管理后台登录密码。

当前默认值：`admin123456`

生产环境建议修改为更强密码。

## JWT 登录认证配置

### `CHAT2API_JWT_SECRET`

后端签发和校验登录 token 的密钥。

当前默认值：`dev-only-change-this-jwt-secret`

注意事项：

- 生产环境必须固定且保密。
- 修改后，已签发的旧登录 token 会失效。

### `CHAT2API_JWT_TTL_SECONDS`

登录 token 有效期，单位为秒。

当前默认值：`86400`

即 24 小时。

## Reporter 注册配置

### `CHAT2API_REPORTER_REGISTRATION_CODE`

Reporter 客户端注册到后端时需要填写的注册码。

当前默认值：`dev-reporter-registration-code`

用途：

- 防止未知 Reporter 随意注册到后端。
- Reporter 首次注册时填写的注册码必须与后端配置一致。

## 跨域配置

### `CHAT2API_CORS_ALLOWED_ORIGINS`

允许访问后端 API 的前端来源地址。

当前默认值：

```text
http://localhost:*,http://127.0.0.1:*
```

默认允许本机开发环境访问后端。

如果管理后台部署到独立域名，例如：

```text
https://admin.example.com
```

则需要把该域名加入此变量。

## 请求大小限制

### `CHAT2API_MAX_REQUEST_BODY_BYTES`

单个 HTTP 请求体最大大小，单位为字节。

当前默认值：`1048576`

即 1MB。

用途：

- 限制异常大请求。
- 降低恶意请求占用服务资源的风险。

## 限流配置

### `CHAT2API_RATE_LIMIT_WINDOW_SECONDS`

限流统计窗口，单位为秒。

当前默认值：`60`

表示按每 60 秒统计一次请求次数。

### `CHAT2API_AUTH_RATE_LIMIT`

登录相关接口的限流次数。

当前默认值：`10`

表示每个限流窗口内，登录相关接口最多允许 10 次请求。

### `CHAT2API_REPORTER_RATE_LIMIT`

Reporter 相关接口的限流次数。

当前默认值：`60`

表示每个限流窗口内，Reporter 相关接口最多允许 60 次请求。

## 代理请求配置

### `CHAT2API_REQUEST_TIMEOUT_MS`

后端请求上游 AI Provider 的超时时间，单位为毫秒。

当前默认值：`60000`

即 60 秒。

### `CHAT2API_RETRY_COUNT`

请求上游 AI Provider 失败时的重试次数。

当前默认值：`3`

## 部署时重点关注项

部署时通常重点检查以下变量：

- `CHAT2API_DB_URL`
- `CHAT2API_DB_USERNAME`
- `CHAT2API_DB_PASSWORD`
- `CHAT2API_ADMIN_USERNAME`
- `CHAT2API_ADMIN_PASSWORD`
- `CHAT2API_ENCRYPTION_KEY`
- `CHAT2API_JWT_SECRET`
- `CHAT2API_REPORTER_REGISTRATION_CODE`
- `CHAT2API_CORS_ALLOWED_ORIGINS`

## 重要提醒

`CHAT2API_ENCRYPTION_KEY` 和 `CHAT2API_JWT_SECRET` 建议在首次部署时就固定好。

尤其是 `CHAT2API_ENCRYPTION_KEY`，如果后端已经保存了账号凭据，再修改它，可能导致之前保存的 token、cookie 等敏感数据无法解密。
