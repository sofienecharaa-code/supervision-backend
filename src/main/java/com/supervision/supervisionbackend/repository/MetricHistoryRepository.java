package com.supervision.supervisionbackend.repository;

import com.supervision.supervisionbackend.model.MetricHistory;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MetricHistoryRepository extends MongoRepository<MetricHistory, String> {
    List<MetricHistory> findTop50ByHostIdOrderByTimestampDesc(String hostId);
    List<MetricHistory> findByHostIdOrderByTimestampAsc(String hostId);
    long countByHostId(String hostId);
    void deleteByHostIdAndIdIn(String hostId, List<String> ids);
}