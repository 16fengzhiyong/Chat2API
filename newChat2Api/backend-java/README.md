# Chat2API Backend

Spring Boot backend for the three-tier Chat2API migration.

## Features

- OpenAI-compatible endpoints:
  - `POST /v1/chat/completions`
  - `POST /v1/completions`
  - `GET /v1/models`
  - `GET /v1/models/{model}`
- Management APIs:
  - `/api/providers`
  - `/api/accounts`
  - `/api/api-keys`
  - `/api/model-mappings`
  - `/api/logs`
  - `/api/config`
  - `/api/sessions`
  - `/api/system-prompts`
  - `/api/tool-calling`
  - `/api/data/export`
- Reporter APIs:
  - `POST /api/reporter/register`
  - `POST /api/reporter/heartbeat`
  - `POST /api/reporter/accounts`
- AES/GCM encrypted account credential persistence.
- Built-in provider seed data and provider forwarder registry.

## Run

```bash
mvn spring-boot:run
```

The current environment used during implementation did not have Maven on `PATH`, so backend compilation must be verified after Maven is installed or through an IDE with Maven support.
