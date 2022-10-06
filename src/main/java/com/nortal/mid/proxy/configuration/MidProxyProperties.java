package com.nortal.mid.proxy.configuration;

import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

import java.time.Duration;

@Value
@ConstructorBinding
@ConfigurationProperties(prefix = "mid-proxy")
public class MidProxyProperties {
    Duration pollingDelay;
    int pollingRetries;
    boolean evictAfterFinalStatusRequest;
}
