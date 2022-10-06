package com.nortal.mid.proxy.configuration;

import ee.sk.mid.MidAuthenticationResponseValidator;
import ee.sk.mid.MidClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.ssl.SSLContextBuilder;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import static org.springframework.util.ResourceUtils.getFile;

@Slf4j
@Configuration
public class MidClientConfiguration {

    @Bean
    public SSLContext midTrustContext(MidClientProperties properties) throws NoSuchAlgorithmException, KeyManagementException, IOException, CertificateException, KeyStoreException {
        return new SSLContextBuilder()
                .setKeyStoreType(properties.getTruststoreType())
                .loadTrustMaterial(getFile(properties.getTruststorePath()), properties.getTruststorePassword().toCharArray())
                .build();
    }

    @Bean
    public KeyStore midTrustStore(MidClientProperties properties, ResourceLoader loader) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        Resource resource = loader.getResource(properties.getTruststorePath());
        KeyStore trustStore = KeyStore.getInstance(properties.getTruststoreType());
        trustStore.load(resource.getInputStream(), properties.getTruststorePassword().toCharArray());
        return trustStore;
    }

    @Bean
    public MidAuthenticationResponseValidator midResponseValidator(KeyStore midTrustStore) {
        return new MidAuthenticationResponseValidator(midTrustStore);
    }

    @Bean
    public MidClient midClient(SSLContext midTrustContext, MidClientProperties properties) {
        ClientConfig clientConfig = midClientConfig(properties);
        return MidClient.newBuilder()
                .withHostUrl(properties.getHostUrl())
                .withRelyingPartyUUID(properties.getRelyingPartyUuid())
                .withRelyingPartyName(properties.getRelyingPartyName())
                .withTrustSslContext(midTrustContext)
                .withNetworkConnectionConfig(clientConfig)
                .withLongPollingTimeoutSeconds((int) properties.getLongPollingTimeout().toSeconds())
                .build();
    }

    private ClientConfig midClientConfig(MidClientProperties properties) {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.property(ClientProperties.CONNECT_TIMEOUT, (int) properties.getConnectionTimeout().toMillis());
        clientConfig.property(ClientProperties.READ_TIMEOUT, (int) properties.getReadTimeout().toMillis());
        return clientConfig;
    }
}
