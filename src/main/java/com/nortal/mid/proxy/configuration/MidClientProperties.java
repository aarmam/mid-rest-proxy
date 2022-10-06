package com.nortal.mid.proxy.configuration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotNull;
import java.time.Duration;

@Getter
@AllArgsConstructor
@ConstructorBinding
@ConfigurationProperties(prefix = "mid-client")
public class MidClientProperties {
    @NotNull
    private String hostUrl;
    @NotNull
    private String truststorePath;
    @NotNull
    private String truststoreType;
    @NotNull
    private String truststorePassword;
    @NotNull
    private String relyingPartyUuid;
    @NotNull
    private String relyingPartyName;
    @NotNull
    private Duration longPollingTimeout;
    @NotNull
    private Duration connectionTimeout;
    @NotNull
    private Duration readTimeout;

    @PostConstruct
    public void validateConfiguration() {
        Assert.isTrue(readTimeout.getSeconds() >= longPollingTimeout.getSeconds() + 5, "Mobile-ID read timeout must be at least 5 seconds longer than its long polling timeout.");
    }
}