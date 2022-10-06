package com.nortal.mid.proxy.controller;

import com.nortal.mid.proxy.controller.MidExceptionHandler.ErrorResponse;
import com.nortal.mid.proxy.model.MidProxyRequest;
import com.nortal.mid.proxy.service.MidProxyService;
import ee.sk.mid.rest.dao.MidSessionStatus;
import ee.sk.mid.rest.dao.request.MidAuthenticationRequest;
import ee.sk.mid.rest.dao.request.MidSignatureRequest;
import ee.sk.mid.rest.dao.response.MidAuthenticationResponse;
import ee.sk.mid.rest.dao.response.MidSignatureResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.nortal.mid.proxy.model.MidProxyRequest.Status.*;

@RestController
@RequiredArgsConstructor
public class MidApiController {
    public static final String MID_API_AUTHENTICATION_REQUEST = "/authentication";
    public static final String MID_API_AUTHENTICATION_STATUS = "/authentication/session/{sessionId}";
    public static final String MID_API_SIGNATURE_REQUEST = "/signature";
    public static final String MID_API_SIGNATURE_STATUS = "/signature/session/{sessionId}";

    private static final MidSessionStatus RUNNING_STATE = new MidSessionStatus();

    static {
        RUNNING_STATE.setState("RUNNING");
    }

    private final MidProxyService midProxyService;

    @PostMapping(MID_API_AUTHENTICATION_REQUEST)
    public MidAuthenticationResponse authenticate(@RequestBody MidAuthenticationRequest request) {
        return midProxyService.authenticate(request);
    }

    @GetMapping(MID_API_AUTHENTICATION_STATUS)
    public ResponseEntity<?> authenticationStatus(@PathVariable String sessionId) {
        return getSessionStatus(sessionId);
    }

    @PostMapping(MID_API_SIGNATURE_REQUEST)
    public MidSignatureResponse signature(@RequestBody MidSignatureRequest request) {
        return midProxyService.sign(request);
    }

    @GetMapping(MID_API_SIGNATURE_STATUS)
    public ResponseEntity<?> signatureStatus(@PathVariable String sessionId) {
        return getSessionStatus(sessionId);
    }

    private ResponseEntity<?> getSessionStatus(String sessionId) {
        MidProxyRequest midProxyRequest = midProxyService.getMidProxyRequest(sessionId);
        if (midProxyRequest == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("SessionID not found"));
        }
        if (midProxyRequest.getPollingStatus() == INITIATED || midProxyRequest.getPollingStatus() == POLLING) {
            return ResponseEntity.ok(RUNNING_STATE);
        } else if (midProxyRequest.getPollingStatus() == EXCEPTION) {
            return ResponseEntity.internalServerError().body(new ErrorResponse("Internal error"));
        } else {
            return ResponseEntity.ok(midProxyRequest.getMidSessionStatus());
        }
    }
}
