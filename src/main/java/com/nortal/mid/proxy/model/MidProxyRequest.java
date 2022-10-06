package com.nortal.mid.proxy.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import ee.sk.mid.rest.dao.MidSessionStatus;
import ee.sk.mid.rest.dao.request.MidAbstractRequest;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

import static com.nortal.mid.proxy.model.MidProxyRequest.Status.POLLING;

@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MidProxyRequest implements Serializable {
    private String sessionId;
    private RequestType requestType;
    private Status pollingStatus;
    private LocalDateTime pollingStatusTimestamp;
    private int pollingCounter;
    private String pollingException;
    private MidAbstractRequest midRequest;
    private MidSessionStatus midSessionStatus;

    public void setPollingStatus(Status pollingStatus) {
        this.pollingStatus = pollingStatus;
        this.pollingStatusTimestamp = LocalDateTime.now();
        if (pollingStatus == POLLING) {
            pollingCounter++;
        }
    }

    @RequiredArgsConstructor
    public enum RequestType {
        AUTH("/authentication"), SIGN("/signature");

        @Getter
        final private String url;
    }

    public enum Status {
        INITIATED, POLLING, RESULT, EXCEPTION
    }
}
