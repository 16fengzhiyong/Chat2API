# Development

## Backend

The backend is a Spring Boot 3.3 / Java 17 application.

```bash
cd backend-java
mvn spring-boot:run
```

If `mvn` is not available on Windows, install Maven and ensure `mvn` is on `PATH`, or run the project from an IDE with Maven support.

Default management credentials:

- Username: `admin`
- Password: `admin123456`

Default database URL points to local MySQL `chat2api`. Override with:

- `CHAT2API_DB_URL`
- `CHAT2API_DB_USERNAME`
- `CHAT2API_DB_PASSWORD`
- `CHAT2API_ENCRYPTION_KEY`

## Admin Web

```bash
cd admin-web
npm install
npm run dev
```

Open `http://localhost:5174` and configure backend URL in Settings.

## Reporter Electron

```bash
cd reporter-electron
npm install
npm run dev
```

Workflow:

1. Configure backend URL.
2. Register reporter client.
3. Choose provider and open login window.
4. Extract credentials automatically or paste JSON manually.
5. Upload account to backend.
