package com.nortal.mid.proxy.service;

import com.nortal.mid.proxy.configuration.MidClientProperties;
import com.nortal.mid.proxy.configuration.MidProxyProperties;
import com.nortal.mid.proxy.model.MidProxyRequest;
import com.nortal.mid.proxy.model.MidProxyRequest.RequestType;
import com.nortal.mid.proxy.model.MidProxyRequest.Status;
import ee.sk.mid.MidClient;
import ee.sk.mid.rest.dao.MidSessionStatus;
import ee.sk.mid.rest.dao.request.MidAbstractRequest;
import ee.sk.mid.rest.dao.request.MidAuthenticationRequest;
import ee.sk.mid.rest.dao.request.MidSignatureRequest;
import ee.sk.mid.rest.dao.response.MidAuthenticationResponse;
import ee.sk.mid.rest.dao.response.MidSignatureResponse;
import io.micrometer.core.instrument.Metrics;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteMessaging;
import org.apache.ignite.IgniteSemaphore;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.nortal.mid.proxy.configuration.IgniteCacheConfiguration.CACHE_MID_REQUEST;
import static com.nortal.mid.proxy.model.MidProxyRequest.RequestType.AUTH;
import static com.nortal.mid.proxy.model.MidProxyRequest.RequestType.SIGN;
import static com.nortal.mid.proxy.model.MidProxyRequest.Status.*;
import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.delayedExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Slf4j
@Service
@RequiredArgsConstructor
public class MidProxyService {
    public static final String STATUS_POLLING_TOPIC = "STATUS_POLLING_TOPIC";

    private final MidClient midClient;
    private final Ignite ignite;
    private final IgniteCache<String, MidProxyRequest> requestCache;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final MidProxyProperties midProxyProperties;
    private final MidClientProperties midClientProperties;
    private IgniteMessaging igniteMessaging;

    @PostConstruct
    private void subscribeToStatusPollingTopic() {
        // Feature: https://ignite.apache.org/docs/latest/messaging
        igniteMessaging = ignite.message(ignite.cluster().forClientNodes(CACHE_MID_REQUEST));
        igniteMessaging.localListen(STATUS_POLLING_TOPIC, (nodeId, sessionId) -> {
            log.info("Session: {}, Received status polling request", sessionId);
            lockSessionAndPollStatus(sessionId.toString());
            return true; // Continue listening
        });
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void checkUnprocessedRequests() {
        long pollingTimeout = midClientProperties.getLongPollingTimeout().toSeconds();

        // Feature: https://ignite.apache.org/docs/latest/key-value-api/using-cache-queries
        // Using QueryFilter to find unprocessed sessions and QueryTransformer to return only subset of properties.
        try (QueryCursor<String> qryCursor = requestCache.withKeepBinary()
                .query(new ScanQuery<>(new UnprocessedRequestsFilter(pollingTimeout, midProxyProperties.getPollingRetries())), new SessionIdQueryTransformer())) {
            qryCursor.forEach(sessionId -> {
                log.info("Session: {}, Unprocessed or failed polling request", sessionId);
                Metrics.counter("mid.proxy.session.retry").increment();
                lockSessionAndPollStatus(sessionId);
            });
        }
    }

    private void lockSessionAndPollStatus(String sessionId) {
        CompletableFuture.runAsync(() -> {

            // Feature: https://ignite.apache.org/docs/latest/data-structures/semaphore
            // If semaphore is not acquired it will be retried by checkUnprocessedRequests()
            IgniteSemaphore semaphore = ignite.semaphore(sessionId, 1, true, true);
            if (semaphore.tryAcquire()) {
                try {
                    MidProxyRequest midProxyRequest = requestCache.get(sessionId);
                    if (midProxyRequest == null) {
                        log.warn("Session: {}, Session not found or expired", sessionId);
                        Metrics.counter("mid.proxy.session.expired").increment();
                        return;
                    } else if (midProxyRequest.getPollingStatus() == RESULT || midProxyRequest.getPollingStatus() == EXCEPTION) {
                        log.warn("Session: {}, Session already processed", sessionId);
                        Metrics.counter("mid.proxy.session.processed").increment();
                        return;
                    }

                    log.info("Session: {}, Distributed lock acquired", sessionId);
                    Metrics.counter("mid.proxy.session.poll").increment();
                    setPollingStatus(midProxyRequest, sessionId, POLLING);
                    MidSessionStatus midSessionStatus = midClient.getSessionStatusPoller().fetchFinalSessionStatus(sessionId, format("%s/session/%s", midProxyRequest.getRequestType().getUrl(), sessionId));
                    log.info("Session: {}, Result: {}, State: {}", sessionId, midSessionStatus.getResult(), midSessionStatus.getState());
                    midProxyRequest.setMidSessionStatus(midSessionStatus);
                    setPollingStatus(midProxyRequest, sessionId, RESULT);
                    Metrics.counter("mid.proxy.session.result").increment();
                } catch (Exception e) {
                    Metrics.counter("mid.proxy.session.exception").increment();
                    log.error("Session: {}, MID polling exception", sessionId, e);
                    MidProxyRequest midProxyRequest = requestCache.get(sessionId);
                    midProxyRequest.setPollingStatus(EXCEPTION);
                    midProxyRequest.setPollingException(e.getMessage());
                    requestCache.put(sessionId, midProxyRequest);
                } finally { // Semaphore release conditions 1) Normal execution 2) Exception occurs 3) Ignite node leaves topology
                    semaphore.release();
                    semaphore.close();
                    log.info("Session: {}, Distributed lock released", sessionId);
                }
            } else {
                log.info("Session: {}, Distributed lock not acquired", sessionId);
            }
        }, delayedExecutor(midProxyProperties.getPollingDelay().toMillis(), MILLISECONDS, taskExecutor));
    }

    public MidAuthenticationResponse authenticate(MidAuthenticationRequest request) {
        Metrics.counter("mid.proxy.session.start").increment();
        MidAuthenticationResponse authenticationResponse = midClient.getMobileIdConnector().authenticate(request);
        String sessionId = authenticationResponse.getSessionID();
        publishPollingJob(request, AUTH, sessionId);
        return authenticationResponse;
    }

    public MidSignatureResponse sign(MidSignatureRequest request) {
        Metrics.counter("mid.proxy.session.start").increment();
        MidSignatureResponse signatureResponse = midClient.getMobileIdConnector().sign(request);
        String sessionId = signatureResponse.getSessionID();
        publishPollingJob(request, SIGN, sessionId);
        return signatureResponse;
    }

    private void publishPollingJob(MidAbstractRequest request, RequestType requestType, String sessionId) {
        MidProxyRequest midProxyRequest = MidProxyRequest.builder()
                .sessionId(sessionId)
                .requestType(requestType)
                .pollingStatus(INITIATED)
                .midRequest(request)
                .pollingStatusTimestamp(LocalDateTime.now())
                .build();
        requestCache.put(sessionId, midProxyRequest);
        igniteMessaging.send(STATUS_POLLING_TOPIC, sessionId);
    }

    public MidProxyRequest getMidProxyRequest(String sessionId) {
        MidProxyRequest midProxyRequest = requestCache.get(sessionId);
        if (midProxyRequest == null) {
            return null;
        }
        Status pollingStatus = midProxyRequest.getPollingStatus();
        if (pollingStatus == RESULT) {
            Metrics.counter("mid.proxy.session.result.requested").increment();
            if (midProxyProperties.isEvictAfterFinalStatusRequest()) {
                requestCache.remove(sessionId);
            }
        }
        return midProxyRequest;
    }

    public List<MidProxyRequest> getMidProxyRequests() {
        return requestCache.withKeepBinary().query(new ScanQuery<>(), new MidRequestQueryTransformer()).getAll();
    }

    private void setPollingStatus(MidProxyRequest midProxyRequest, String sessionId, Status status) {
        midProxyRequest.setPollingStatus(status);
        requestCache.put(sessionId, midProxyRequest);
        log.info("Session: {}, Status: {}", sessionId, status);
    }

    @PreDestroy
    @SneakyThrows
    public void onDestroy() {
        long timeout = midClientProperties.getLongPollingTimeout().toSeconds();
        long currentCount = 0;
        log.info("Graceful shutdown in progress!");
        while (taskExecutor.getActiveCount() != 0 && currentCount++ <= timeout) {
            log.info("Nr. of active status polling jobs left: {}. Timeout in: {}", taskExecutor.getActiveCount(), timeout - currentCount);
            Thread.sleep(1000);
        }
        log.info("Continuing shutdown!");
    }
}
