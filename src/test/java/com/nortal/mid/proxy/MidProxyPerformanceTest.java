package com.nortal.mid.proxy;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

public class MidProxyPerformanceTest extends Simulation {
    String midRestProxyUrl = System.getProperty("midRestProxyUrl", "http://localhost:8080");
    HttpProtocolBuilder httpProtocol = http
            .disableUrlEncoding()
            .disableFollowRedirect()
            .silentResources()
            .acceptHeader("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .acceptLanguageHeader("en-US,en;q=0.5")
            .acceptEncodingHeader("gzip, deflate")
            .userAgentHeader("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36");

    public MidProxyPerformanceTest() {
        String authenticationUrl = midRestProxyUrl + "/authentication";
        String sessionStatusUrl = midRestProxyUrl + "/authentication/session/#{session_id}";
        ScenarioBuilder scenario = scenario("Single client authentication scenario")
                .exec(http("/authentication")
                        .post(authenticationUrl)
                        .asJson().body(RawFileBody("mid_authentication_request.json"))
                        .check(status().is(200))
                        .check(jsonPath("$.sessionID").exists().saveAs("session_id")))
                .exitHereIfFailed()
                .tryMax(24, "session polling loop")
                .on(pause(Duration.ofSeconds(5))
                        .exec(http("/authentication/session")
                                .get(sessionStatusUrl)
                                .check(status().is(200).saveAs("http_status"))
                                .check(jsonPath("$.state").is("COMPLETE"))
                                .check(jsonPath("$.result").is("OK"))
                                .check(jsonPath("$.cert").exists())
                                .check(jsonPath("$.signature.algorithm").is("SHA384withECDSA"))
                                .check(jsonPath("$.signature.value").exists()))
                );

        setUp(scenario.injectOpen(constantUsersPerSec(10).during(Duration.ofSeconds(300)))).protocols(httpProtocol);
    }
}
