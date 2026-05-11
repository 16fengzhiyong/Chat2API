package com.chat2api.backend.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class SseStreamWriter {
    private final OutputStream out;
    private final ObjectMapper objectMapper;

    public SseStreamWriter(OutputStream out, ObjectMapper objectMapper) {
        this.out = out;
        this.objectMapper = objectMapper;
    }

    public void writeEvent(Object data) throws IOException {
        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        } catch (Exception error) {
            json = "{}";
        }
        writeRaw("data: " + json + "\n\n");
    }

    public void writeRaw(String sseText) throws IOException {
        out.write(sseText.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    public void writeDone() throws IOException {
        writeRaw("data: [DONE]\n\n");
    }

    public void writeErrorAndDone(String message) throws IOException {
        String safe = message == null ? "Request failed" : message;
        String json = "{\"error\":{\"message\":" + escape(safe) + ",\"type\":\"chat2api_error\"}}";
        writeRaw("data: " + json + "\n\n");
        writeDone();
    }

    private String escape(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
