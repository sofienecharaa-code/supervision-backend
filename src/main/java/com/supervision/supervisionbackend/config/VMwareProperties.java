package com.supervision.supervisionbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "vmware")
@Data
public class VMwareProperties {
    private String host;
    private String username;
    private String password;
    private boolean enabled;
}