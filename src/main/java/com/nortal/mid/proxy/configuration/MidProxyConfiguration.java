package com.nortal.mid.proxy.configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.opencensus.exporter.stats.prometheus.PrometheusStatsCollector;
import io.prometheus.client.CollectorRegistry;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MidProxyConfiguration {

    @Bean
    public CollectorRegistry collectorRegistry() {
        PrometheusStatsCollector.createAndRegister();
        return CollectorRegistry.defaultRegistry; // Use common collector registry provided by OpenCensus in /actuator/prometheus endpoint
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder.serializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
