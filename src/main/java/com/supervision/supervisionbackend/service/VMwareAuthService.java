package com.supervision.supervisionbackend.service;

import com.supervision.supervisionbackend.config.VMwareProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;

/**
 * Gère l'authentification auprès de l'API vSphere REST (vCenter ou ESXi).
 * Documentation officielle : https://developer.vmware.com/apis/vsphere-automation/latest/
 */
@Service
@Slf4j
public class VMwareAuthService {

    private final VMwareProperties vmwareProperties;
    private final RestTemplate restTemplate;

    private String cachedSessionToken;

    public VMwareAuthService(VMwareProperties vmwareProperties,
                             @Qualifier("vmwareRestTemplate") RestTemplate restTemplate) {
        this.vmwareProperties = vmwareProperties;
        this.restTemplate = restTemplate;
    }

    public String getSessionToken() {
        if (cachedSessionToken == null) {
            cachedSessionToken = login();
        }
        return cachedSessionToken;
    }

    private String login() {
        String url = vmwareProperties.getHost() + "/api/session";

        String credentials = vmwareProperties.getUsername() + ":" + vmwareProperties.getPassword();
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encodedCredentials);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request = new HttpEntity<>("{}", headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, String.class
            );
            String token = response.getBody();
            if (token != null) {
                token = token.replace("\"", "");
            }
            log.info("Connexion VMware réussie");
            return token;
        } catch (Exception e) {
            log.error("Échec de connexion à VMware vSphere: {}", e.getMessage());
            throw new RuntimeException("Impossible de se connecter à VMware", e);
        }
    }

    public void invalidateSession() {
        cachedSessionToken = null;
    }
}