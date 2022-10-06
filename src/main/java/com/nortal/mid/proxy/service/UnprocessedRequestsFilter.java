package com.nortal.mid.proxy.service;

import com.nortal.mid.proxy.model.MidProxyRequest;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.internal.binary.BinaryEnumObjectImpl;
import org.apache.ignite.lang.IgniteBiPredicate;

import java.time.LocalDateTime;

import static com.nortal.mid.proxy.model.MidProxyRequest.Status.*;

public final class UnprocessedRequestsFilter implements IgniteBiPredicate<String, BinaryObject> {
    private final int pollingRetries;
    private final LocalDateTime initiatedOrExceptionTimeout;
    private final LocalDateTime runningTimeout;

    public UnprocessedRequestsFilter(long pollingTimeout, int pollingRetries) {
        this.pollingRetries = pollingRetries;
        this.initiatedOrExceptionTimeout = LocalDateTime.now().minusSeconds(5);
        this.runningTimeout = LocalDateTime.now().minusSeconds(pollingTimeout);
    }

    @Override
    public boolean apply(String sessionId, BinaryObject bo) {
        LocalDateTime statusTimestamp = bo.field("pollingStatusTimestamp");
        int statusOrdinal = bo.<BinaryEnumObjectImpl>field("pollingStatus").enumOrdinal();
        MidProxyRequest.Status status = MidProxyRequest.Status.values()[statusOrdinal];
        boolean isInitiatedOrException = (status == INITIATED || status == EXCEPTION) && statusTimestamp.isBefore(initiatedOrExceptionTimeout);
        boolean isRunningTimeout = status == POLLING && statusTimestamp.isBefore(runningTimeout);
        return bo.<Integer>field("pollingCounter") <= pollingRetries && (isInitiatedOrException || isRunningTimeout);
    }
}
