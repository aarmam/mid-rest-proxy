package com.nortal.mid.proxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class MidProxyApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(MidProxyApplication.class);
        application.run(args);
    }
}
