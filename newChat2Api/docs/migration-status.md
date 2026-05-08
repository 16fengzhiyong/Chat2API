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
- Backend session management with multi-turn history persistence, timeout configuration, and session management APIs.
- Backend context management with sliding-window and token-limit trimming configuration.
- Backend tool-calling compatibility layer with managed XML / bracket prompt injection and non-stream response parsing into OpenAI `tool_calls`.
- Backend stream compatibility wrapper that can convert non-stream OpenAI responses to OpenAI SSE chunks for `stream: true`.
- Provider maintenance service for clear-chat / credits / model-refresh entry points, including real clear-chat calls for Qwen AI, Z.ai, and Perplexity where credentials are available.
- Built-in provider forwarder now has private payload skeletons for Qwen AI, Z.ai, Perplexity, and MiniMax instead of only generic OpenAI-compatible passthrough.

## Provider migration notes

The current backend contains a provider adapter registry and built-in provider forwarder foundation. The exact private API behavior from the original Electron project still needs to be migrated provider-by-provider:

- DeepSeek request format, token validation, stream parsing, clear chat.
- GLM refresh token validation, signing/header details, stream parsing, clear chat.
- Kimi gRPC-like request format, JWT validation, stream parsing.
- Qwen and Qwen AI cookie/session behavior, record mode, dynamic chat endpoints, stream parsing, clear chat.
- MiniMax JWT + realUserID behavior, signing, credits API, clear chat.
- Mimo multi-cookie validation, query format, stream parsing, clear chat.
- Z.ai private API format, create-chat handshake, request signing, browser fingerprint query, clear chat, and basic SSE-to-OpenAI parsing.
- Perplexity SSE/search response handling.

## Current precision limits

- DeepSeek, GLM, Kimi, Qwen, Mimo, MiniMax, Z.ai, Qwen AI, and Perplexity private streaming parsers still need provider-by-provider exact Java ports from the original TypeScript stream handlers.
- Z.ai has a dedicated Java forwarder with create-chat, HMAC request signing, browser fingerprint query parameters, model casing, and basic thinking/answer SSE parsing. Exact incremental streaming behavior still needs a servlet streaming implementation instead of buffered RestTemplate response handling.
- MiniMax signed device registration, credit query, chat list, polling stream, and delete-chat sequence are not yet fully ported.
- Qwen AI still needs Java-side create-chat handshake parity before every completion for strict compatibility.
- Perplexity still needs the original SSE response parser ported for exact OpenAI chunk mapping.

## Verification notes

- Maven is not installed on the current Windows environment, so `mvn test` could not run here.
- Admin Web production build passed with `npm.cmd run build`.
- Reporter Electron unpacked Windows build passed with `npm.cmd run build -- --dir`.
