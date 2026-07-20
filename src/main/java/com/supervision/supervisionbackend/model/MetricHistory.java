package com.supervision.supervisionbackend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "metric_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetricHistory {

    @Id
    private String id;

    private String hostId;
    private String hostName;
    private Instant timestamp;
    private double cpuUsage;
    private double ramUsage;
    private double storageUsage;
}