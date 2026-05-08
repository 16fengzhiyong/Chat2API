# newChat2Api

Chat2API full three-tier rewrite.

## Modules

- `backend-java`: Spring Boot backend for OpenAI-compatible proxy, management APIs, account storage, logs, sessions, tool calling, and reporter upload APIs.
- `admin-web`: React web management console.
- `reporter-electron`: Electron client dedicated to account login, token/cookie extraction, and account upload.
- `docs`: deployment and operation documentation.

## Quick start

### Backend

```bash
cd backend-java
mvn spring-boot:run
```

Required environment variables for production:

- `CHAT2API_DB_URL`
- `CHAT2API_DB_USERNAME`
- `CHAT2API_DB_PASSWORD`
- `CHAT2API_ENCRYPTION_KEY`
- `CHAT2API_ADMIN_USERNAME`
- `CHAT2API_ADMIN_PASSWORD`

### Admin Web

```bash
cd admin-web
npm install
npm run dev
```

### Reporter Electron

```bash
cd reporter-electron
npm install
npm run dev
```
