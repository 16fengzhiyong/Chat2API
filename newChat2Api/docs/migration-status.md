# Migration Status

## Implemented foundation

- Spring Boot backend skeleton with MySQL JPA persistence.
- Built-in provider seed data for DeepSeek, GLM, Kimi, Qwen, Qwen AI, Z.ai, MiniMax, Mimo, and Perplexity.
- Encrypted account credential storage with AES/GCM.
- Management APIs for providers, accounts, API keys, model mappings, logs, statistics, sessions, system prompts, configuration, and tool-calling configuration.
- Reporter APIs for desktop reporter registration, heartbeat, and account upload.
- OpenAI-compatible endpoints for `/v1/chat/completions`, `/v1/completions`, `/v1/models`, and `/v1/models/{model}`.
- Web admin shell for dashboard, providers, accounts, API keys, model mappings, sessions, prompts/tool calling, logs, and settings.
- Electron reporter shell for backend binding, provider login, cookie/localStorage/header extraction, manual JSON fallback, heartbeat, and account upload.

## Provider migration notes

The current backend contains a provider adapter registry and built-in provider forwarder foundation. The exact private API behavior from the original Electron project still needs to be migrated provider-by-provider:

- DeepSeek request format, token validation, stream parsing, clear chat.
- GLM refresh token validation, signing/header details, stream parsing, clear chat.
- Kimi gRPC-like request format, JWT validation, stream parsing.
- Qwen and Qwen AI cookie/session behavior, record mode, dynamic chat endpoints, stream parsing, clear chat.
- MiniMax JWT + realUserID behavior, signing, credits API, clear chat.
- Mimo multi-cookie validation, query format, stream parsing, clear chat.
- Z.ai private API format, stream parsing, clear chat.
- Perplexity SSE/search response handling.

## Verification notes

- Maven is not installed on the current Windows environment, so `mvn test` could not run here.
- Node is available, but package installation/build has not been run to avoid unapproved network dependency installation.
