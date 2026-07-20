package com.supervision.supervisionbackend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "virtual_machines")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VirtualMachine {

    @Id
    private String id;

    private String name;
    private String hostId;
    private String hostName;
    private String type;
    private String os;
    private String status;
    private double cpuUsage;
    private double ramUsage;
    private double storageUsage;
    private int vcpuCount;
    private double ramAllocatedGb;
    private String externalId;
}