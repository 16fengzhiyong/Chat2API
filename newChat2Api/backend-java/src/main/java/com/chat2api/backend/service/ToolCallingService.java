package com.chat2api.backend.service;

import com.chat2api.backend.domain.AppConfigEntity;
import com.chat2api.backend.repository.AppConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ToolCallingService {
    private static final String CONFIG_KEY = "toolCalling";
    private final AppConfigRepository appConfigRepository;
    private final ObjectMapper objectMapper;

    public ToolCallingService(AppConfigRepository appConfigRepository, ObjectMapper objectMapper) {
        this.appConfigRepository = appConfigRepository;
        this.objectMapper = objectMapper;
    }

    public TransformResult transformRequest(Map<String, Object> request) {
        Map<String, Object> config = config();
        List<Map<String, Object>> tools = tools(request.get("tools"));
        if (!Boolean.TRUE.equals(config.get("enabled")) || tools.isEmpty()) {
            return new TransformResult(request, false, tools, String.valueOf(config.getOrDefault("protocol", "managed_xml")));
        }
        if ("native".equals(config.get("mode"))) {
            return new TransformResult(request, false, tools, String.valueOf(config.getOrDefault("protocol", "managed_xml")));
        }
        String protocol = String.valueOf(config.getOrDefault("protocol", "managed_xml"));
        Map<String, Object> updated = new LinkedHashMap<>(request);
        updated.put("messages", injectPrompt(messages(request.get("messages")), renderPrompt(protocol, tools, config)));
        updated.remove("tools");
        updated.remove("tool_choice");
        return new TransformResult(updated, true, tools, protocol);
    }

    public String applyNonStreamResponse(String body, TransformResult transform) {
        if (!transform.injected() || body == null || body.isBlank()) {
            return body;
        }
        try {
            Map<String, Object> response = objectMapper.readValue(body, new TypeReference<>() {});
            Map<String, Object> message = firstMessage(response);
            Object contentValue = message.get("content");
            if (!(contentValue instanceof String content) || content.isBlank()) {
                return body;
            }
            ParseResult parsed = parse(content, transform.protocol(), transform.tools());
            if (parsed.toolCalls().isEmpty()) {
                return body;
            }
            message.put("content", parsed.content().isBlank() ? null : parsed.content());
            message.put("tool_calls", parsed.toolCalls());
            firstChoice(response).put("finish_reason", "tool_calls");
            return objectMapper.writeValueAsString(response);
        } catch (Exception error) {
            return body;
        }
    }

    public Map<String, Object> config() {
        return appConfigRepository.findById(CONFIG_KEY)
                .map(AppConfigEntity::getConfigValue)
                .map(this::parseConfig)
                .orElseGet(this::defaultConfig);
    }

    public String defaultConfigJson() {
        try {
            return objectMapper.writeValueAsString(defaultConfig());
        } catch (Exception error) {
            return "{}";
        }
    }

    private Map<String, Object> parseConfig(String json) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, Object> merged = defaultConfig();
            merged.putAll(parsed);
            return merged;
        } catch (Exception error) {
            return defaultConfig();
        }
    }

    private Map<String, Object> defaultConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", true);
        config.put("mode", "prompt-injection");
        config.put("clientAdapter", "openai");
        config.put("protocol", "managed_xml");
        config.put("diagnostics", false);
        return config;
    }

    private String renderPrompt(String protocol, List<Map<String, Object>> tools, Map<String, Object> config) {
        String toolList = renderToolList(tools);
        String prompt;
        if ("managed_bracket".equals(protocol)) {
            prompt = "## Available Tools\nYou can invoke the following developer tools. Tool names are case-sensitive.\n\n" + toolList + "\n\nWhen calling tools, respond with only this block:\n\n[function_calls]\n[call:exact_tool_name]{\"argument\":\"value\"}[/call]\n[/function_calls]";
        } else {
            prompt = "## Available Tools\nYou can invoke the following developer tools. Tool names are case-sensitive.\nUse only the exact tool names listed below. Do not rename, camelCase, translate, shorten, or invent tool names.\n\n" + toolList + "\n\nWhen calling tools, respond with only this Chat2API XML block:\n\n<|CHAT2API|tool_calls><|CHAT2API|invoke name=\"exact_tool_name\"><|CHAT2API|parameter name=\"argument\"><![CDATA[value]]></|CHAT2API|parameter></|CHAT2API|invoke></|CHAT2API|tool_calls>";
        }
        Object custom = config.get("customPromptTemplate");
        if (custom instanceof String template && !template.isBlank()) {
            return template.replace("{{tools}}", prompt).replace("{{tool_names}}", String.join(", ", toolNames(tools))).replace("{{format}}", protocol);
        }
        return prompt;
    }

    private String renderToolList(List<Map<String, Object>> tools) {
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> tool : tools) {
            Map<String, Object> function = function(tool);
            lines.add("- " + function.getOrDefault("name", "unknown") + ": " + function.getOrDefault("description", "") + " Parameters: " + function.getOrDefault("parameters", Map.of()));
        }
        return String.join("\n", lines);
    }

    private List<Map<String, Object>> injectPrompt(List<Map<String, Object>> messages, String prompt) {
        List<Map<String, Object>> result = new ArrayList<>(messages);
        if (!result.isEmpty() && "system".equals(result.get(0).get("role"))) {
            Map<String, Object> first = new LinkedHashMap<>(result.get(0));
            first.put("content", String.valueOf(first.getOrDefault("content", "")) + "\n\n" + prompt);
            result.set(0, first);
            return result;
        }
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("role", "system");
        system.put("content", prompt);
        result.add(0, system);
        return result;
    }

    private ParseResult parse(String content, String protocol, List<Map<String, Object>> tools) {
        if ("managed_bracket".equals(protocol)) {
            return parseBracket(content, tools);
        }
        ParseResult xml = parseXml(content, tools);
        return xml.toolCalls().isEmpty() ? parseBracket(content, tools) : xml;
    }

    private ParseResult parseBracket(String content, List<Map<String, Object>> tools) {
        List<String> allowedNames = toolNames(tools);
        List<Map<String, Object>> calls = new ArrayList<>();
        List<String> rawMatches = new ArrayList<>();
        Matcher blockMatcher = Pattern.compile("\\[function_calls\\]([\\s\\S]*?)\\[/function_calls\\]").matcher(content);
        while (blockMatcher.find()) {
            rawMatches.add(blockMatcher.group(0));
            Matcher callMatcher = Pattern.compile("\\[call:([^\\]]+)\\]([\\s\\S]*?)\\[/call\\]").matcher(blockMatcher.group(1));
            while (callMatcher.find()) {
                String name = callMatcher.group(1).trim();
                if (allowedNames.contains(name)) {
                    calls.add(toolCall(calls.size(), name, callMatcher.group(2).trim()));
                }
            }
        }
        return new ParseResult(removeRaw(content, rawMatches), calls);
    }

    private ParseResult parseXml(String content, List<Map<String, Object>> tools) {
        List<String> allowedNames = toolNames(tools);
        List<Map<String, Object>> calls = new ArrayList<>();
        List<String> rawMatches = new ArrayList<>();
        Pattern blockPattern = Pattern.compile("<\\|CHAT2API\\|tool_calls>([\\s\\S]*?)</\\|CHAT2API\\|tool_calls>|<tool_calls>([\\s\\S]*?)</tool_calls>");
        Matcher blockMatcher = blockPattern.matcher(content);
        while (blockMatcher.find()) {
            rawMatches.add(blockMatcher.group(0));
            String block = blockMatcher.group(1) == null ? blockMatcher.group(2) : blockMatcher.group(1);
            Matcher invokeMatcher = Pattern.compile("(?:<\\|CHAT2API\\|invoke|<invoke)\\s+name=\"([^\"]+)\"\\s*>([\\s\\S]*?)(?:</\\|CHAT2API\\|invoke>|</invoke>)").matcher(block);
            while (invokeMatcher.find()) {
                String name = invokeMatcher.group(1).trim();
                if (!allowedNames.contains(name)) {
                    continue;
                }
                Map<String, Object> args = new LinkedHashMap<>();
                Matcher parameterMatcher = Pattern.compile("(?:<\\|CHAT2API\\|parameter|<parameter)\\s+name=\"([^\"]+)\"\\s*>([\\s\\S]*?)(?:</\\|CHAT2API\\|parameter>|</parameter>)").matcher(invokeMatcher.group(2));
                while (parameterMatcher.find()) {
                    args.put(parameterMatcher.group(1).trim(), cleanupCdata(parameterMatcher.group(2).trim()));
                }
                calls.add(toolCall(calls.size(), name, toJson(args)));
            }
        }
        return new ParseResult(removeRaw(content, rawMatches), calls);
    }

    private Map<String, Object> toolCall(int index, String name, String arguments) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("arguments", normalizeArguments(arguments));
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("id", "call_" + index);
        call.put("index", index);
        call.put("type", "function");
        call.put("function", function);
        return call;
    }

    private String normalizeArguments(String arguments) {
        String trimmed = arguments == null ? "{}" : arguments.trim();
        if (trimmed.isBlank()) {
            return "{}";
        }
        try {
            objectMapper.readTree(trimmed);
            return trimmed;
        } catch (Exception error) {
            return toJson(Map.of("input", trimmed));
        }
    }

    private String cleanupCdata(String value) {
        return value.replaceFirst("^<!\\[CDATA\\[", "").replaceFirst("\\]\\]>$", "");
    }

    private String removeRaw(String content, List<String> rawMatches) {
        String cleaned = content;
        for (String raw : rawMatches) {
            cleaned = cleaned.replace(raw, "");
        }
        return cleaned.trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> tools(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> tool = new LinkedHashMap<>();
                map.forEach((key, val) -> tool.put(String.valueOf(key), val));
                result.add(tool);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> messages(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> message = new LinkedHashMap<>();
                map.forEach((key, val) -> message.put(String.valueOf(key), val));
                result.add(message);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> function(Map<String, Object> tool) {
        Object function = tool.get("function");
        if (function instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, val) -> result.put(String.valueOf(key), val));
            return result;
        }
        return tool;
    }

    private List<String> toolNames(List<Map<String, Object>> tools) {
        return tools.stream().map(this::function).map(tool -> String.valueOf(tool.getOrDefault("name", ""))).filter(name -> !name.isBlank()).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstChoice(Map<String, Object> response) {
        Object choices = response.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstMessage(Map<String, Object> response) {
        Object message = firstChoice(response).get("message");
        if (message instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    public record TransformResult(Map<String, Object> request, boolean injected, List<Map<String, Object>> tools, String protocol) {}
    private record ParseResult(String content, List<Map<String, Object>> toolCalls) {}
}
