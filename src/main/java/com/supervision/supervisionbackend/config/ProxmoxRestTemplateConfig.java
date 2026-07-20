package com.supervision.supervisionbackend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

/**
 * Proxmox utilise généralement un certificat auto-signé.
 * Ce RestTemplate dédié désactive la vérification stricte SSL
 * UNIQUEMENT pour les appels vers Proxmox, en environnement de dev/test.
 * À ne jamais faire vers un service en production sans certificat valide.
 */
@Configuration
@RequiredArgsConstructor
public class ProxmoxRestTemplateConfig {

    private final ProxmoxProperties proxmoxProperties;

    @Bean
    @Qualifier("proxmoxRestTemplate")
    public RestTemplate proxmoxRestTemplate() throws Exception {
        if (!proxmoxProperties.isVerifySsl()) {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        }
        return new RestTemplate(new SimpleClientHttpRequestFactory());
    }
}