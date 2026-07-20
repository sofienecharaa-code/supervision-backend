package com.supervision.supervisionbackend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "hosts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Host {

    @Id
    private String id;

    private String name;
    private String type;       // "VMware" ou "Proxmox"
    private String ipAddress;
    private String status;     // "online", "offline", "error"
    private double cpuUsage;   // en %
    private double ramUsage;   // en %
    private double storageUsage; // en %
}