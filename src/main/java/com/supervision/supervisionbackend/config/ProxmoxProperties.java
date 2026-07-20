package com.supervision.supervisionbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "proxmox")
@Data
public class ProxmoxProperties {
    private String host;
    private String token;
    private boolean enabled;
    private boolean verifySsl;
}