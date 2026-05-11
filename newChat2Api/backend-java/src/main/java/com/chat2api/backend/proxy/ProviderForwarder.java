package com.chat2api.backend.proxy;

import com.chat2api.backend.domain.AccountEntity;
import com.chat2api.backend.domain.ProviderEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public interface ProviderForwarder {
    boolean supports(String vendor);
    ForwardResult forward(ProviderEntity provider, AccountEntity account, Map<String, String> credentials, Map<String, Object> request, String actualModel);

    default void forwardStreaming(ProviderEntity provider, AccountEntity account,
                                  Map<String, String> credentials, Map<String, Object> request,
                                  String actualModel, SseStreamWriter writer, Consumer<String> onComplete) throws Exception {
        Map<String, Object> nonStreamRequest = new LinkedHashMap<>(request);
        nonStreamRequest.put("stream", false);
        ForwardResult result = forward(provider, account, credentials, nonStreamRequest, actualModel);
        if (!result.success()) {
            writer.writeErrorAndDone(result.errorMessage());
            return;
        }
        String body = result.body() == null ? "" : result.body();
        if (!body.isBlank()) {
            if (body.trim().startsWith("data:")) {
                writer.writeRaw(body.endsWith("\n\n") ? body : body + "\n\n");
            } else {
                writer.writeRaw("data: " + body + "\n\ndata: [DONE]\n\n");
            }
        } else {
            writer.writeDone();
        }
        onComplete.accept(body);
    }
}
