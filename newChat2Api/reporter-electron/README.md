# Chat2API Reporter Electron

Electron account reporting client. It only performs provider login, token/cookie extraction, and upload to the Java backend.

## Run

```bash
npm install
npm run dev
```

## Build

```bash
npm run build -- --dir
```

The unpacked Windows build passed in the implementation environment.

## Workflow

1. Configure backend URL.
2. Register the reporter client.
3. Select a provider and open the login window.
4. Wait for automatic extraction or paste credentials JSON manually.
5. Upload the account to backend.
