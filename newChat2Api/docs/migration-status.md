# Migration Status

## Implemented foundation

- Spring Boot backend skeleton with MySQL JPA persistence.
- Built-in provider seed data for DeepSeek, GLM, Kimi, Qwen, Qwen AI, Z.ai, MiniMax, Mimo, and Perplexity.
- Encrypted account credential storage with AES/GCM.
- Management APIs for providers, accounts, API keys, model mappings, logs, statistics, sessions, system prompts, configuration, and tool-calling configuration.
- Reporter APIs for desktop reporter registration, heartbeat, and account upload.
- OpenAI-compatible endpoints for `/v1/chat/completions`, `/v1/completions`, `/v1/models`, and `/v1/models/{model}`.
- Web admin shell for dashboard, providers, accounts, API keys, model mappings, sessions, prompts/tool calling, logs, and settings.
- Web admin management actions for provider/account JSON create-edit-delete, account validation, API key creation/deletion, model mapping CRUD, session deletion/clear, system prompt CRUD, tool/context config save, and data import/export.
- Electron reporter shell for backend binding, provider login, cookie/localStorage/header extraction, manual JSON fallback, heartbeat, and account upload.
- Backend session management with multi-turn history persistence, timeout configuration, and session management APIs.
- Backend context management with sliding-window and token-limit trimming configuration.
- Backend tool-calling compatibility layer with managed XML / bracket prompt injection and non-stream response parsing into OpenAI `tool_calls`.
- Backend stream compatibility wrapper that can convert non-stream OpenAI responses to OpenAI SSE chunks for `stream: true`.
- Provider maintenance service for clear-chat / credits / model-refresh entry points, including real clear-chat calls for Qwen AI, Z.ai, and Perplexity where credentials are available.
- Dedicated provider forwarders are implemented for Z.ai and Qwen AI overseas, and both take precedence over generic built-in forwarding.
- Backend Java is now managed by Gradle wrapper files under `backend-java`.

## Provider migration notes

The current backend contains a provider adapter registry and built-in provider forwarder foundation. Z.ai and Qwen AI overseas are the completed private-provider migration targets for now. Other provider migrations are paused unless explicitly reprioritized:

- DeepSeek request format, token validation, stream parsing, clear chat.
- GLM refresh token validation, signing/header details, stream parsing, clear chat.
- Kimi gRPC-like request format, JWT validation, stream parsing.
- Qwen domestic behavior, record mode, stream parsing, clear chat. Migration is intentionally skipped for now.
- Qwen AI cookie authentication, create-chat handshake, dynamic chat_id endpoint, private request body, and basic SSE-to-OpenAI parsing.
- MiniMax JWT + realUserID behavior, signing, credits API, clear chat.
- Mimo multi-cookie validation, query format, stream parsing, clear chat.
- Z.ai private API format, create-chat handshake, request signing, browser fingerprint query, clear chat, and basic SSE-to-OpenAI parsing.
- Perplexity SSE/search response handling. Migration is intentionally skipped for now.

## Current precision limits

- DeepSeek, GLM, Kimi, Mimo, MiniMax, Qwen domestic, and Perplexity private migrations are paused while non-provider product features are completed.
- Z.ai has a dedicated Java forwarder with create-chat, HMAC request signing, browser fingerprint query parameters, model casing, and basic thinking/answer SSE parsing. Exact incremental streaming behavior still needs a servlet streaming implementation instead of buffered RestTemplate response handling.
- Qwen AI has a dedicated Java forwarder split by responsibility into protocol construction, forward orchestration, and stream parsing. Exact incremental streaming behavior still needs a servlet streaming implementation instead of buffered RestTemplate response handling.
- MiniMax signed device registration, credit query, chat list, polling stream, and delete-chat sequence are not yet fully ported.
- Qwen AI still needs delete-after-chat integration with backend session policy for strict single-turn cleanup parity.
- Perplexity and Qwen domestic edition are skipped until explicitly reprioritized.

## Verification notes

- Backend Java uses Gradle. `gradlew.bat build` was started during migration but not awaited.
- Admin Web production build passed with `npm.cmd run build`.
- Reporter Electron unpacked Windows build passed with `npm.cmd run build -- --dir`.
