package com.supervision.supervisionbackend.service;

import com.supervision.supervisionbackend.model.Host;
import com.supervision.supervisionbackend.model.MetricHistory;
import com.supervision.supervisionbackend.repository.HostRepository;
import com.supervision.supervisionbackend.repository.MetricHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricHistoryService {

    private static final int MAX_POINTS_PER_HOST = 50;

    private final MetricHistoryRepository historyRepository;
    private final HostRepository hostRepository;

    /**
     * Enregistre un instantané des métriques actuelles pour chaque host connu.
     * Appelé après chaque cycle de synchronisation (Proxmox + VMware).
     */
    public void recordSnapshot() {
        List<Host> hosts = hostRepository.findAll();

        for (Host host : hosts) {
            MetricHistory entry = new MetricHistory();
            entry.setHostId(host.getId());
            entry.setHostName(host.getName());
            entry.setTimestamp(Instant.now());
            entry.setCpuUsage(host.getCpuUsage());
            entry.setRamUsage(host.getRamUsage());
            entry.setStorageUsage(host.getStorageUsage());

            historyRepository.save(entry);

            trimOldEntries(host.getId());
        }
    }

    /**
     * Garde uniquement les MAX_POINTS_PER_HOST entrées les plus récentes par host,
     * supprime le reste pour éviter une croissance infinie de la collection.
     */
    private void trimOldEntries(String hostId) {
        long count = historyRepository.countByHostId(hostId);
        if (count <= MAX_POINTS_PER_HOST) return;

        List<MetricHistory> recent = historyRepository.findTop50ByHostIdOrderByTimestampDesc(hostId);
        List<String> keepIds = recent.stream().map(MetricHistory::getId).collect(Collectors.toList());

        List<MetricHistory> all = historyRepository.findByHostIdOrderByTimestampAsc(hostId);
        List<String> toDelete = all.stream()
                .map(MetricHistory::getId)
                .filter(id -> !keepIds.contains(id))
                .collect(Collectors.toList());

        if (!toDelete.isEmpty()) {
            historyRepository.deleteByHostIdAndIdIn(hostId, toDelete);
        }
    }

    public List<MetricHistory> getHistoryForHost(String hostId) {
        return historyRepository.findByHostIdOrderByTimestampAsc(hostId);
    }
}