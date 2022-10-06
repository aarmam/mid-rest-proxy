package com.nortal.mid.proxy.configuration;

import com.nortal.mid.proxy.model.MidProxyRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.DeploymentMode;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.events.CacheEvent;
import org.apache.ignite.events.EventType;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.ignite.spi.metric.opencensus.OpenCensusMetricExporterSpi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.apache.ignite.IgniteSystemProperties.IGNITE_NO_SHUTDOWN_HOOK;

@Slf4j
@Configuration
public class IgniteCacheConfiguration {
    public static final String CACHE_MID_REQUEST = "MID-REQUEST";

    @Bean
    public Ignite ignite(@Value("${mid-proxy.ignite.nodes}") List<String> igniteNodes) throws IOException {
        IgniteConfiguration cfg = new IgniteConfiguration();
        cfg.setClientMode(true);
        cfg.setPeerClassLoadingEnabled(true);
        cfg.setDeploymentMode(DeploymentMode.CONTINUOUS);
        cfg.setGridLogger(new Slf4jLogger());

        OpenCensusMetricExporterSpi openCensusMetricExporterSpi = new OpenCensusMetricExporterSpi();
        openCensusMetricExporterSpi.setPeriod(1_000L);
        cfg.setMetricExporterSpi(openCensusMetricExporterSpi);

        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
        ipFinder.setAddresses(igniteNodes);
        TcpDiscoverySpi tcpDiscoverySpi = new TcpDiscoverySpi();
        cfg.setDiscoverySpi(tcpDiscoverySpi.setIpFinder(ipFinder));

        System.setProperty(IGNITE_NO_SHUTDOWN_HOOK, "true");
        Ignite ignite = Ignition.start(cfg);
        ignite.events(ignite.cluster().forCacheNodes(CACHE_MID_REQUEST)).remoteListen((UUID uuid, CacheEvent event) -> {
            log.info(String.format("CACHE_OBJECT_EXPIRED: cacheName=%s, key=%s", event.cacheName(), event.key().toString()));
            return true;
        }, null, EventType.EVT_CACHE_OBJECT_EXPIRED);
        return ignite;
    }

    @Bean
    public IgniteCache<String, MidProxyRequest> requestCache(Ignite ignite) {
        return ignite.getOrCreateCache(CACHE_MID_REQUEST);
    }
}
