package com.nortal.mid.proxy.service;

import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.lang.IgniteClosure;

import javax.cache.Cache;

public final class SessionIdQueryTransformer implements IgniteClosure<Cache.Entry<String, BinaryObject>, String> {

    @Override
    public String apply(Cache.Entry<String, BinaryObject> entry) {
        return entry.getKey();
    }
}
