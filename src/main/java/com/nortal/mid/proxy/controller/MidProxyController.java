package com.nortal.mid.proxy.controller;

import com.nortal.mid.proxy.model.MidProxyRequest;
import com.nortal.mid.proxy.service.MidProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MidProxyController {
    private final MidProxyService midProxyService;

    @GetMapping("/mid-proxy-requests")
    public List<MidProxyRequest> midProxyRequests() {
        return midProxyService.getMidProxyRequests();
    }
}
