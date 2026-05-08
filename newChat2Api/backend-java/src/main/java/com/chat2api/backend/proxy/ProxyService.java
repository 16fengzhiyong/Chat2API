package com.chat2api.backend.proxy;

import com.chat2api.backend.domain.RequestLogEntity;
import com.chat2api.backend.service.AccountService;
import com.chat2api.backend.service.ContextManagementService;
import com.chat2api.backend.service.LoadBalancerService;
import com.chat2api.backend.service.RequestLogService;
import com.chat2api.backend.service.SessionService;
import com.chat2api.backend.service.ToolCallingService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ProxyService {
    private final LoadBalancerService loadBalancerService;
    private final AccountService accountService;
    private final RequestLogService requestLogService;
    private final SessionService sessionService;
    private final ContextManagementService contextManagementService;
    private final ToolCallingService toolCallingService;
    private final List<ProviderForwarder> forwarders;

    public ProxyService(LoadBalancerService loadBalancerService, AccountService accountService, RequestLogService requestLogService, SessionService sessionService, ContextManagementService contextManagementService, ToolCallingService toolCallingService, List<ProviderForwarder> forwarders) {
        this.loadBalancerService = loadBalancerService;
        this.accountService = accountService;
        this.requestLogService = requestLogService;
        this.sessionService = sessionService;
        this.contextManagementService = contextManagementService;
        this.toolCallingService = toolCallingService;
        this.forwarders = forwarders;
    }

    public ForwardResult chatCompletion(Map<String, Object> request) {
        Instant startedAt = Instant.now();
        String model = String.valueOf(request.getOrDefault("model", ""));
        RequestLogEntity log = new RequestLogEntity();
        log.setMethod("POST");
        log.setUrl("/v1/chat/completions");
        log.setModel(model);
        log.setStreamRequest(Boolean.TRUE.equals(request.get("stream")));
        log.setRequestBody(requestLogService.toJson(request));
        try {
            LoadBalancerService.Selection selection = loadBalancerService.select(model).orElseThrow(() -> new IllegalStateException("No available account for model: " + model));
            log.setProviderId(selection.provider().getId());
            log.setAccountId(selection.account().getId());
            log.setActualModel(selection.actualModel());
            Map<String, String> credentials = accountService.credentials(selection.account().getId());
            ProviderForwarder forwarder = forwarders.stream().filter(item -> item.supports(selection.provider().getVendor())).findFirst().orElseThrow();
            SessionService.SessionContext sessionContext = sessionService.prepare(request, selection.provider().getId(), selection.account().getId(), selection.actualModel());
            Map<String, Object> sessionRequest = sessionContext.request();
            Map<String, Object> contextRequest = contextManagementService.apply(sessionRequest);
            ToolCallingService.TransformResult toolTransform = toolCallingService.transformRequest(contextRequest);
            ForwardResult result = forwarder.forward(selection.provider(), selection.account(), credentials, toolTransform.request(), selection.actualModel());
            String responseBody = toolCallingService.applyNonStreamResponse(result.body(), toolTransform);
            if (result.success()) {
                result = ForwardResult.ok(result.statusCode(), result.contentType(), responseBody);
                sessionService.complete(sessionContext, sessionRequest, responseBody);
            }
            log.setLatency(Duration.between(startedAt, Instant.now()).toMillis());
            log.setStatus(result.success() ? "success" : "failed");
            log.setStatusCode(result.statusCode());
            log.setResponseBody(result.body());
            log.setErrorMessage(result.errorMessage());
            requestLogService.save(log);
            if (result.success()) {
                accountService.touchSuccess(selection.account().getId());
            }
            return result;
        } catch (Exception error) {
            log.setLatency(Duration.between(startedAt, Instant.now()).toMillis());
            log.setStatus("failed");
            log.setStatusCode(500);
            log.setErrorMessage(error.getMessage());
            requestLogService.save(log);
            return ForwardResult.fail(500, error.getMessage());
        }
    }
}
