package com.chat2api.backend.proxy;

import com.chat2api.backend.domain.AccountEntity;
import com.chat2api.backend.domain.ProviderEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
@Order(-18)
public class DeepSeekProviderForwarder implements ProviderForwarder {
    private static final String COMPLETION_TARGET_PATH = "/api/v0/chat/completion";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final DeepSeekProtocol protocol;
    private final DeepSeekPowSolver powSolver;
    private final DeepSeekStreamParser streamParser;

    public DeepSeekProviderForwarder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.protocol = new DeepSeekProtocol(objectMapper);
        this.powSolver = new DeepSeekPowSolver(objectMapper);
        this.streamParser = new DeepSeekStreamParser(objectMapper);
    }

    @Override
    public boolean supports(String vendor) {
        return "deepseek".equals(vendor);
    }

    @Override
    public ForwardResult forward(ProviderEntity provider, AccountEntity account, Map<String, String> credentials, Map<String, Object> request, String actualModel) {
        try {
            String refreshToken = protocol.refreshToken(credentials);
            if (refreshToken == null || refreshToken.isBlank()) {
                return ForwardResult.fail(401, "DeepSeek token is not configured");
            }
            String accessToken = acquireToken(refreshToken);
            String cookie = protocol.cookie(credentials);
            String sessionId = createSession(accessToken, cookie);
            String prompt = protocol.messagesToPrompt(request);
            DeepSeekChatOptions options = DeepSeekChatOptions.resolve(request, prompt);
            Map<String, Object> challenge = createChallenge(accessToken, COMPLETION_TARGET_PATH);
            String powResponse = powSolver.solve(challenge);
            HttpHeaders headers = protocol.headers(accessToken, cookie, "https://chat.deepseek.com/");
            headers.set("X-Ds-Pow-Response", powResponse);
            ResponseEntity<String> response = restTemplate.exchange(
                    URI.create(DeepSeekProtocol.BASE_URL + "/v0/chat/completion"),
                    HttpMethod.POST,
                    new HttpEntity<>(objectMapper.writeValueAsString(protocol.completionBody(request, sessionId, prompt, options)), headers),
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                return ForwardResult.fail(response.getStatusCode().value(), response.getBody() == null ? "DeepSeek request failed" : response.getBody());
            }
            String upstream = response.getBody() == null ? "" : response.getBody();
            boolean stream = Boolean.TRUE.equals(request.get("stream"));
            return stream
                    ? ForwardResult.ok(200, "text/event-stream; charset=utf-8", streamParser.toOpenAiStream(upstream, sessionId, actualModel, request))
                    : ForwardResult.ok(200, MediaType.APPLICATION_JSON_VALUE, streamParser.toOpenAiJson(upstream, sessionId, actualModel, request));
        } catch (HttpStatusCodeException error) {
            return ForwardResult.fail(error.getStatusCode().value(), error.getResponseBodyAsString());
        } catch (Exception error) {
            return ForwardResult.fail(502, error.getMessage());
        }
    }

    private String acquireToken(String refreshToken) throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                URI.create(DeepSeekProtocol.BASE_URL + "/v0/users/current"),
                HttpMethod.GET,
                new HttpEntity<>(null, protocol.headers(refreshToken, null, "https://chat.deepseek.com/")),
                String.class
        );
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Failed to acquire DeepSeek token: HTTP " + response.getStatusCode().value());
        }
        Map<String, Object> parsed = objectMapper.readValue(response.getBody() == null ? "{}" : response.getBody(), new TypeReference<>() {});
        Object token = protocol.nested(parsed, "data", "biz_data", "token");
        if (token == null) {
            token = protocol.nested(parsed, "biz_data", "token");
        }
        if (token == null || String.valueOf(token).isBlank()) {
            throw new IllegalStateException("Failed to acquire DeepSeek token");
        }
        return String.valueOf(token);
    }

    private String createSession(String accessToken, String cookie) throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                URI.create(DeepSeekProtocol.BASE_URL + "/v0/chat_session/create"),
                HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(protocol.sessionBody()), protocol.headers(accessToken, cookie, "https://chat.deepseek.com/")),
                String.class
        );
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Failed to create DeepSeek session: HTTP " + response.getStatusCode().value());
        }
        Map<String, Object> parsed = objectMapper.readValue(response.getBody() == null ? "{}" : response.getBody(), new TypeReference<>() {});
        Object id = protocol.nested(parsed, "data", "biz_data", "chat_session", "id");
        if (id == null) {
            id = protocol.nested(parsed, "biz_data", "chat_session", "id");
        }
        if (id == null || String.valueOf(id).isBlank()) {
            throw new IllegalStateException("Failed to create DeepSeek session: missing session id");
        }
        return String.valueOf(id);
    }

    @Override
    public void forwardStreaming(ProviderEntity provider, AccountEntity account,
                                  Map<String, String> credentials, Map<String, Object> request,
                                  String actualModel, SseStreamWriter writer, Consumer<String> onComplete) throws Exception {
        String refreshToken = protocol.refreshToken(credentials);
        if (refreshToken == null || refreshToken.isBlank()) {
            writer.writeErrorAndDone("DeepSeek token is not configured");
            return;
        }
        String accessToken = acquireToken(refreshToken);
        String cookie = protocol.cookie(credentials);
        String sessionId = createSession(accessToken, cookie);
        String prompt = protocol.messagesToPrompt(request);
        DeepSeekChatOptions options = DeepSeekChatOptions.resolve(request, prompt);
        Map<String, Object> challenge = createChallenge(accessToken, COMPLETION_TARGET_PATH);
        String powResponse = powSolver.solve(challenge);
        HttpHeaders headers = protocol.headers(accessToken, cookie, "https://chat.deepseek.com/");
        headers.set("X-Ds-Pow-Response", powResponse);
        byte[] bodyBytes = objectMapper.writeValueAsBytes(
                protocol.completionBody(request, sessionId, prompt, options));

        String model = String.valueOf(request.getOrDefault("model", "")).toLowerCase();
        boolean thinkingModel = model.contains("think") || model.contains("r1") || model.contains("reasoner") || request.get("reasoning_effort") != null;
        DeepSeekStreamParser.StreamingContext ctx = new DeepSeekStreamParser.StreamingContext(thinkingModel);
        long created = Instant.now().getEpochSecond();
        boolean[] roleEmitted = {false};

        try {
            restTemplate.execute(
                    URI.create(DeepSeekProtocol.BASE_URL + "/v0/chat/completion"),
                    HttpMethod.POST,
                    req -> {
                        req.getHeaders().putAll(headers);
                        req.getBody().write(bodyBytes);
                    },
                    response -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                DeepSeekStreamParser.LineDelta delta = streamParser.processLine(line, ctx);
                                if (delta.isEmpty()) continue;
                                String chunkId = sessionId + "@" + (ctx.messageId().isBlank() ? "pending" : ctx.messageId());
                                try {
                                    if (!roleEmitted[0]) {
                                        writer.writeEvent(sseChunk(chunkId, actualModel, created, Map.of("role", "assistant", "content", ""), null));
                                        roleEmitted[0] = true;
                                    }
                                    if (!delta.reasoningDelta().isBlank()) {
                                        writer.writeEvent(sseChunk(chunkId, actualModel, created, Map.of("reasoning_content", delta.reasoningDelta()), null));
                                    }
                                    if (!delta.contentDelta().isBlank()) {
                                        writer.writeEvent(sseChunk(chunkId, actualModel, created, Map.of("content", delta.contentDelta()), null));
                                    }
                                } catch (IOException e) {
                                    throw e;
                                } catch (Exception e) {
                                    throw new IOException(e.getMessage(), e);
                                }
                            }
                        }
                        return null;
                    });
        } catch (HttpStatusCodeException error) {
            writer.writeErrorAndDone("DeepSeek request failed: HTTP " + error.getStatusCode().value());
            return;
        }

        String id = sessionId + "@" + ctx.messageId();
        writer.writeEvent(sseChunk(id, actualModel, created, Map.of(), "stop"));
        writer.writeDone();
        onComplete.accept(buildCompletionJson(actualModel, id, created, ctx.accumulatedContent(), ctx.accumulatedReasoning()));
    }

    private Map<String, Object> sseChunk(String id, String model, long created, Map<String, Object> delta, String finishReason) {
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("delta", delta);
        choice.put("finish_reason", finishReason);
        Map<String, Object> chunk = new LinkedHashMap<>();
        chunk.put("id", id);
        chunk.put("model", model);
        chunk.put("object", "chat.completion.chunk");
        chunk.put("choices", List.of(choice));
        chunk.put("created", created);
        return chunk;
    }

    private String buildCompletionJson(String model, String id, long created, String content, String reasoning) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content);
        if (!reasoning.isBlank()) {
            message.put("reasoning_content", reasoning);
        }
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("object", "chat.completion");
        response.put("created", created);
        response.put("model", model);
        response.put("choices", List.of(choice));
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception error) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createChallenge(String accessToken, String targetPath) throws Exception {
        ResponseEntity<String> response = restTemplate.exchange(
                URI.create(DeepSeekProtocol.BASE_URL + "/v0/chat/create_pow_challenge"),
                HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(protocol.challengeBody(targetPath)), protocol.headers(accessToken, null, "https://chat.deepseek.com/")),
                String.class
        );
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Failed to get DeepSeek challenge: HTTP " + response.getStatusCode().value());
        }
        Map<String, Object> parsed = objectMapper.readValue(response.getBody() == null ? "{}" : response.getBody(), new TypeReference<>() {});
        Object challenge = protocol.nested(parsed, "data", "biz_data", "challenge");
        if (challenge == null) {
            challenge = protocol.nested(parsed, "biz_data", "challenge");
        }
        if (!(challenge instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Failed to get DeepSeek challenge");
        }
        return (Map<String, Object>) map;
    }
}
