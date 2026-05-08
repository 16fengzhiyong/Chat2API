package com.chat2api.backend.domain;

public enum LoadBalanceStrategy {
    ROUND_ROBIN,
    FILL_FIRST,
    FAILOVER
}
