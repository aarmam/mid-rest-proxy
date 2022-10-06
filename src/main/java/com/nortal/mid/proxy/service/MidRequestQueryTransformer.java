package com.nortal.mid.proxy.service;

import com.nortal.mid.proxy.model.MidProxyRequest;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.internal.binary.BinaryEnumObjectImpl;
import org.apache.ignite.lang.IgniteClosure;

import javax.cache.Cache;

public final class MidRequestQueryTransformer implements IgniteClosure<Cache.Entry<String, BinaryObject>, MidProxyRequest> {

    @Override
    public MidProxyRequest apply(Cache.Entry<String, BinaryObject> entry) {
        BinaryObject bo = entry.getValue();
        int statusOrdinal = bo.<BinaryEnumObjectImpl>field("pollingStatus").enumOrdinal();
        int requestTypeOrdinal = bo.<BinaryEnumObjectImpl>field("requestType").enumOrdinal();
        return MidProxyRequest.builder()
                .sessionId(entry.getKey())
                .requestType(MidProxyRequest.RequestType.values()[requestTypeOrdinal])
                .pollingStatus(MidProxyRequest.Status.values()[statusOrdinal])
                .pollingStatusTimestamp(bo.field("pollingStatusTimestamp"))
                .pollingCounter(bo.field("pollingCounter"))
                .pollingException(bo.field("pollingException"))
                .build();
    }
}
