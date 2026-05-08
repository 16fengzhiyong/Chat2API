package com.chat2api.backend.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Service
@Order(100)
public class GenericOpenAiForwarder extends BaseProviderForwarder {
    public GenericOpenAiForwarder(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public boolean supports(String vendor) {
        return true;
    }
}
