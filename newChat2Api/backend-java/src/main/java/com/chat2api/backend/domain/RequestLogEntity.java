package com.chat2api.backend.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "request_logs")
public class RequestLogEntity {
    @Id
    private String id;
    private Instant timestamp = Instant.now();
    private String status;
    private int statusCode;
    private String method;
    private String url;
    private String model;
    private String actualModel;
    private String providerId;
    private String accountId;
    private long latency;
    private boolean streamRequest;
    @Lob
    private String requestBody;
    @Lob
    private String responseBody;
    @Lob
    private String errorMessage;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getActualModel() { return actualModel; }
    public void setActualModel(String actualModel) { this.actualModel = actualModel; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public long getLatency() { return latency; }
    public void setLatency(long latency) { this.latency = latency; }
    public boolean isStreamRequest() { return streamRequest; }
    public void setStreamRequest(boolean streamRequest) { this.streamRequest = streamRequest; }
    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
