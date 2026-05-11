package com.chat2api.backend.proxy;

import com.chat2api.backend.domain.AccountEntity;
import com.chat2api.backend.domain.ProviderEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import com.chat2api.backend.repository.SessionRepository;

import java.net.URI;
import java.util.Map;

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
            QwenAiSessionStore.State state = sessionStore.load(request, options.recordMode());
            String chatId = state.hasChat() ? state.chatId() : createChat(modelId, chatMode, authCookie);
            String parentId = state.hasChat() ? state.parentId() : "";
            ResponseEntity<String> response = post(
                    QwenAiProtocol.BASE_URL + "/api/v2/chat/completions?chat_id=" + chatId,
                    protocol.completionBody(request, modelId, chatId, parentId, chatMode, thinkingMode),
                    authCookie,
                    "completion",
                    chatId
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                return ForwardResult.fail(response.getStatusCode().value(), response.getBody() == null ? "Qwen AI request failed" : response.getBody());
            }
            String upstream = response.getBody() == null ? "" : response.getBody();
            QwenAiStreamParser.ParsedStream parsed = streamParser.parsed(upstream, chatId);
            sessionStore.save(request, options.recordMode(), chatId, parsed.responseId());
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

    private String createChat(String modelId, String chatMode, String authCookie) throws Exception {
        ResponseEntity<String> response = post(
                QwenAiProtocol.BASE_URL + "/api/v2/chats/new",
                protocol.newChatBody(modelId, chatMode),
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
}
