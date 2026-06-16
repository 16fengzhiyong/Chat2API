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
import com.chat2api.backend.repository.SessionRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
@Order(-15)
public class QwenAiProviderForwarder implements ProviderForwarder {
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final QwenAiProtocol protocol = new QwenAiProtocol();
    private final QwenAiStreamParser streamParser;
    private final QwenAiSessionStore sessionStore;

    public QwenAiProviderForwarder(ObjectMapper objectMapper, SessionRepository sessionRepository) {
        this.objectMapper = objectMapper;
        this.streamParser = new QwenAiStreamParser(objectMapper);
        this.sessionStore = new QwenAiSessionStore(sessionRepository, objectMapper);
    }

    @Override
    public boolean supports(String vendor) {
        return "qwen-ai".equals(vendor);
    }

    @Override
    public ForwardResult forward(ProviderEntity provider, AccountEntity account, Map<String, String> credentials, Map<String, Object> request, String actualModel) {
        try {
            String authCookie = protocol.authCookie(credentials);
            if (authCookie == null || authCookie.isBlank()) {
                return ForwardResult.fail(401, "Qwen AI cookies are not configured");
            }
            String originalModel = request.get("model") == null ? actualModel : String.valueOf(request.get("model"));
            String modelId = protocol.mapModel(provider, actualModel);
            QwenAiOptions options = QwenAiOptions.from(provider);
            String thinkingMode = protocol.thinkingMode(request, originalModel, actualModel, options);
            String chatMode = options.chatMode();
            String chatType = protocol.chatType(request, originalModel, actualModel, options);
            QwenAiSessionStore.State state = sessionStore.load(request, options.recordMode(), chatMode, chatType);
            String chatId = state.hasChat() ? state.chatId() : createChat(modelId, chatMode, chatType, authCookie);
            String parentId = state.hasChat() ? state.parentId() : "";

            boolean isVideo = protocol.isVideoChatType(chatType);

            ResponseEntity<String> response = post(
                    QwenAiProtocol.BASE_URL + "/api/v2/chat/completions?chat_id=" + chatId,
                    protocol.completionBody(request, modelId, chatId, parentId, chatMode, thinkingMode, chatType),
                    authCookie,
                    "completion",
                    chatId
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                return ForwardResult.fail(response.getStatusCode().value(), response.getBody() == null ? "Qwen AI request failed" : response.getBody());
            }
            String upstream = response.getBody() == null ? "" : response.getBody();

            // Video: poll task and return result
            if (isVideo) {
                String taskId = protocol.parseVideoTaskId(upstream);
                if (taskId.isBlank()) {
                    return ForwardResult.fail(502, "Failed to extract video task ID");
                }
                String videoResult = pollVideoTask(taskId, authCookie);
                if (videoResult.startsWith("error:")) {
                    String errorMsg = videoResult.substring(6);
                    return ForwardResult.fail(502, "Video generation failed: " + errorMsg);
                }
                return ForwardResult.ok(200, MediaType.APPLICATION_JSON_VALUE, buildVideoJson(modelId, videoResult));
            }

            // Text / Image: parse SSE stream
            QwenAiStreamParser.ParsedStream parsed = streamParser.parsed(upstream, chatId);
            sessionStore.save(request, options.recordMode(), chatMode, chatType, chatId, parsed.responseId());
            boolean stream = Boolean.TRUE.equals(request.get("stream"));
            return stream
                    ? ForwardResult.ok(200, "text/event-stream; charset=utf-8", streamParser.toOpenAiStream(parsed, actualModel))
                    : ForwardResult.ok(200, MediaType.APPLICATION_JSON_VALUE, streamParser.toOpenAiJson(parsed, actualModel));
        } catch (HttpStatusCodeException error) {
            return ForwardResult.fail(error.getStatusCode().value(), error.getResponseBodyAsString());
        } catch (Exception error) {
            return ForwardResult.fail(502, error.getMessage());
        }
    }

    private String createChat(String modelId, String chatMode, String chatType, String authCookie) throws Exception {
        ResponseEntity<String> response = post(
                QwenAiProtocol.BASE_URL + "/api/v2/chats/new",
                protocol.newChatBody(modelId, chatMode, chatType),
                authCookie,
                "new-chat",
                null
        );
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Failed to create Qwen AI chat: HTTP " + response.getStatusCode().value());
        }
        String responseBody = response.getBody();
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("Failed to create Qwen AI chat: empty response");
        }
        Map<String, Object> parsed = objectMapper.readValue(responseBody, new TypeReference<>() {});
        Object data = parsed.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object id = dataMap.get("id");
            if (id != null && !String.valueOf(id).isBlank()) {
                return String.valueOf(id);
            }
        }
        Object id = parsed.get("id");
        if (id != null && !String.valueOf(id).isBlank()) {
            return String.valueOf(id);
        }
        throw new IllegalStateException("Failed to create Qwen AI chat: missing chat id");
    }

    private ResponseEntity<String> post(String url, Object body, String authCookie, String context, String chatId) throws Exception {
        return restTemplate.exchange(
                URI.create(url),
                HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(body), protocol.headers(authCookie, context, chatId)),
                String.class
        );
    }

    private String pollVideoTask(String taskId, String authCookie) {
        int maxPolls = 300;
        int pollInterval = 1000;
        for (int i = 0; i < maxPolls; i++) {
            try {
                Thread.sleep(pollInterval);
                ResponseEntity<String> response = restTemplate.exchange(
                        URI.create(protocol.taskStatusUrl(taskId)),
                        HttpMethod.GET,
                        new HttpEntity<>(protocol.headers(authCookie, "default", null)),
                        String.class
                );
                String body = response.getBody();
                if (body == null) continue;
                Map<String, Object> parsed = objectMapper.readValue(body, new TypeReference<>() {});
                String status = parsed.get("task_status") == null ? "" : String.valueOf(parsed.get("task_status"));
                if ("success".equals(status)) {
                    return String.valueOf(parsed.getOrDefault("content", ""));
                }
                if ("failed".equals(status) || "error".equals(status)) {
                    return "error:" + String.valueOf(parsed.getOrDefault("message", "Unknown error"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "error:Polling interrupted";
            } catch (Exception e) {
                return "error:" + e.getMessage();
            }
        }
        return "error:Video generation timed out after 5 minutes";
    }

    private String buildVideoJson(String model, String videoUrl) throws Exception {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", videoUrl);
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", "stop");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", "video-" + System.currentTimeMillis());
        response.put("object", "chat.completion");
        response.put("created", Instant.now().getEpochSecond());
        response.put("model", model);
        response.put("choices", List.of(choice));
        return objectMapper.writeValueAsString(response);
    }

    @Override
    public void forwardStreaming(ProviderEntity provider, AccountEntity account,
                                  Map<String, String> credentials, Map<String, Object> request,
                                  String actualModel, SseStreamWriter writer, Consumer<String> onComplete) throws Exception {
        String authCookie = protocol.authCookie(credentials);
        if (authCookie == null || authCookie.isBlank()) {
            writer.writeErrorAndDone("Qwen AI cookies are not configured");
            return;
        }
        String originalModel = request.get("model") == null ? actualModel : String.valueOf(request.get("model"));
        String modelId = protocol.mapModel(provider, actualModel);
        QwenAiOptions options = QwenAiOptions.from(provider);
        String thinkingMode = protocol.thinkingMode(request, originalModel, actualModel, options);
        String chatMode = options.chatMode();
        String chatType = protocol.chatType(request, originalModel, actualModel, options);
        QwenAiSessionStore.State state = sessionStore.load(request, options.recordMode(), chatMode, chatType);
        String chatId = state.hasChat() ? state.chatId() : createChat(modelId, chatMode, chatType, authCookie);
        String parentId = state.hasChat() ? state.parentId() : "";

        // Video: non-streaming request → poll → stream SSE
        if (protocol.isVideoChatType(chatType)) {
            ResponseEntity<String> response = post(
                    QwenAiProtocol.BASE_URL + "/api/v2/chat/completions?chat_id=" + chatId,
                    protocol.completionBody(request, modelId, chatId, parentId, chatMode, thinkingMode, chatType),
                    authCookie,
                    "completion",
                    chatId
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                writer.writeErrorAndDone("Qwen AI request failed: HTTP " + response.getStatusCode().value());
                return;
            }
            String upstream = response.getBody() == null ? "" : response.getBody();
            String taskId = protocol.parseVideoTaskId(upstream);
            if (taskId.isBlank()) {
                writer.writeErrorAndDone("Failed to extract video task ID");
                return;
            }
            pollVideoTaskStreaming(taskId, authCookie, actualModel, chatId, writer, onComplete);
            return;
        }

        // Text / Image: streaming request
        HttpHeaders headers = protocol.headers(authCookie, "completion", chatId);
        byte[] bodyBytes = objectMapper.writeValueAsBytes(
                protocol.completionBody(request, modelId, chatId, parentId, chatMode, thinkingMode, chatType));
        String url = QwenAiProtocol.BASE_URL + "/api/v2/chat/completions?chat_id=" + chatId;

        long created = Instant.now().getEpochSecond();
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        StringBuilder summaryReasoning = new StringBuilder();
        String[] responseIdArr = {chatId};
        boolean[] roleEmitted = {false};
        String[] finishReasonArr = {"stop"};

        try {
            restTemplate.execute(URI.create(url), HttpMethod.POST,
                    req -> {
                        req.getHeaders().putAll(headers);
                        req.getBody().write(bodyBytes);
                    },
                    resp -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(resp.getBody(), StandardCharsets.UTF_8))) {
                            StringBuilder eventBuf = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("data:")) {
                                    eventBuf.append(line.substring(5).trim());
                                } else if (line.isBlank() && eventBuf.length() > 0) {
                                    String eventData = eventBuf.toString();
                                    eventBuf.setLength(0);
                                    if ("[DONE]".equals(eventData)) break;
                                    try {
                                        processQwenChunk(eventData, writer, content, reasoning, summaryReasoning,
                                                responseIdArr, roleEmitted, finishReasonArr, actualModel, created);
                                    } catch (Exception e) {
                                        throw new IOException(e.getMessage(), e);
                                    }
                                }
                            }
                            if (eventBuf.length() > 0 && !"[DONE]".equals(eventBuf.toString())) {
                                try {
                                    processQwenChunk(eventBuf.toString(), writer, content, reasoning, summaryReasoning,
                                            responseIdArr, roleEmitted, finishReasonArr, actualModel, created);
                                } catch (Exception ignored) {}
                            }
                        }
                        return null;
                    });
        } catch (HttpStatusCodeException error) {
            writer.writeErrorAndDone("Qwen AI request failed: HTTP " + error.getStatusCode().value());
            return;
        }

        writer.writeEvent(sseChunk(responseIdArr[0], actualModel, created, Map.of(), finishReasonArr[0]));
        writer.writeDone();
        sessionStore.save(request, options.recordMode(), chatMode, chatType, chatId, responseIdArr[0]);
        String finalReasoning = reasoning.isEmpty() ? summaryReasoning.toString() : reasoning.toString();
        onComplete.accept(buildCompletionJson(actualModel, responseIdArr[0], created, content.toString(), finalReasoning));
    }

    private void pollVideoTaskStreaming(String taskId, String authCookie, String model, String chatId,
                                         SseStreamWriter writer, Consumer<String> onComplete) throws Exception {
        long created = Instant.now().getEpochSecond();
        writer.writeEvent(sseChunk(chatId, model, created, Map.of("role", "assistant", "content", ""), null));

        int maxPolls = 300;
        int pollInterval = 1000;
        for (int i = 0; i < maxPolls; i++) {
            try {
                Thread.sleep(pollInterval);
                ResponseEntity<String> response = restTemplate.exchange(
                        URI.create(protocol.taskStatusUrl(taskId)),
                        HttpMethod.GET,
                        new HttpEntity<>(protocol.headers(authCookie, "default", null)),
                        String.class
                );
                String body = response.getBody();
                if (body == null) continue;
                Map<String, Object> parsed = objectMapper.readValue(body, new TypeReference<>() {});
                String status = parsed.get("task_status") == null ? "" : String.valueOf(parsed.get("task_status"));
                if ("success".equals(status)) {
                    String videoUrl = String.valueOf(parsed.getOrDefault("content", ""));
                    if (!videoUrl.isBlank()) {
                        writer.writeEvent(sseChunk(chatId, model, created, Map.of("content", videoUrl), null));
                    }
                    writer.writeEvent(sseChunk(chatId, model, created, Map.of(), "stop"));
                    writer.writeDone();
                    onComplete.accept(buildCompletionJson(model, chatId, created, videoUrl, ""));
                    return;
                }
                if ("failed".equals(status) || "error".equals(status)) {
                    String msg = String.valueOf(parsed.getOrDefault("message", "Unknown error"));
                    writer.writeEvent(sseChunk(chatId, model, created, Map.of("content", "[Video generation failed: " + msg + "]"), "error"));
                    writer.writeDone();
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                writer.writeErrorAndDone("Video polling interrupted");
                return;
            } catch (Exception e) {
                if (e instanceof java.io.IOException) throw (java.io.IOException) e;
                writer.writeErrorAndDone("Video polling error: " + e.getMessage());
                return;
            }
        }
        writer.writeEvent(sseChunk(chatId, model, created, Map.of("content", "[Video generation timed out]"), "stop"));
        writer.writeDone();
    }

    @SuppressWarnings("unchecked")
    private void processQwenChunk(String eventData, SseStreamWriter writer,
                                   StringBuilder content, StringBuilder reasoning, StringBuilder summaryReasoning,
                                   String[] responseId, boolean[] roleEmitted,
                                   String[] finishReason, String model, long created) throws Exception {
        Map<String, Object> event = objectMapper.readValue(eventData, new TypeReference<>() {});
        Object createdEvent = event.get("response.created");
        if (createdEvent instanceof Map<?, ?> cm) {
            Object rid = cm.get("response_id");
            if (rid != null && !String.valueOf(rid).isBlank()) {
                responseId[0] = String.valueOf(rid);
            }
        }
        Object respId = event.get("response_id");
        if (respId != null && !String.valueOf(respId).isBlank()) {
            responseId[0] = String.valueOf(respId);
        }
        Object choices = event.get("choices");
        if (!(choices instanceof List<?> choiceList) || choiceList.isEmpty()) return;
        if (!(choiceList.get(0) instanceof Map<?, ?> choice)) return;
        Object deltaVal = choice.get("delta");
        if (!(deltaVal instanceof Map<?, ?> delta)) return;
        String phase = delta.get("phase") == null ? "" : String.valueOf(delta.get("phase"));
        String status = delta.get("status") == null ? "" : String.valueOf(delta.get("status"));
        String text = delta.get("content") == null ? "" : String.valueOf(delta.get("content"));
        if (!roleEmitted[0]) {
            writer.writeEvent(sseChunk(responseId[0], model, created, Map.of("role", "assistant", "content", ""), null));
            roleEmitted[0] = true;
        }
        if ("think".equals(phase) && !"finished".equals(status) && !text.isBlank()) {
            reasoning.append(text);
            writer.writeEvent(sseChunk(responseId[0], model, created, Map.of("reasoning_content", text), null));
        } else if ("thinking_summary".equals(phase) && reasoning.isEmpty()) {
            String summary = summary(delta.get("extra"));
            if (summary.length() > summaryReasoning.length()) {
                String deltaText = summary.substring(summaryReasoning.length());
                summaryReasoning.setLength(0);
                summaryReasoning.append(summary);
                if (!deltaText.isBlank()) {
                    writer.writeEvent(sseChunk(responseId[0], model, created, Map.of("reasoning_content", deltaText), null));
                }
            }
        } else if (("answer".equals(phase) || "image_gen".equals(phase) || (phase.isBlank() && !text.isBlank())) && !text.isBlank()) {
            content.append(text);
            writer.writeEvent(sseChunk(responseId[0], model, created, Map.of("content", text), null));
            if ("finished".equals(status)) {
                Object fr = delta.get("finish_reason");
                if (fr != null && !String.valueOf(fr).isBlank()) {
                    finishReason[0] = String.valueOf(fr);
                }
            }
        }
    }

    private String summary(Object extra) {
        if (!(extra instanceof Map<?, ?> extraMap)) {
            return "";
        }
        Object summaryThought = extraMap.get("summary_thought");
        if (!(summaryThought instanceof Map<?, ?> summaryMap)) {
            return "";
        }
        Object value = summaryMap.get("content");
        if (value instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    parts.add(String.valueOf(item));
                }
            }
            return String.join("\n", parts);
        }
        return value == null ? "" : String.valueOf(value);
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
}
