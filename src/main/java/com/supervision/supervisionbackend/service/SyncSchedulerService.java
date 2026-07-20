package com.supervision.supervisionbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncSchedulerService {

    private final ProxmoxSyncService proxmoxSyncService;
    private final VMwareSyncService vmwareSyncService;
    private final MetricHistoryService metricHistoryService;

    @Scheduled(fixedDelayString = "${sync.interval-ms:60000}")
    public void runScheduledSync() {
        log.info("--- Démarrage de la synchronisation automatique ---");
        try {
            proxmoxSyncService.syncAll();
        } catch (Exception e) {
            log.error("Erreur lors de la synchronisation Proxmox planifiée: {}", e.getMessage());
        }

        try {
            vmwareSyncService.syncAll();
        } catch (Exception e) {
            log.error("Erreur lors de la synchronisation VMware planifiée: {}", e.getMessage());
        }

        try {
            metricHistoryService.recordSnapshot();
        } catch (Exception e) {
            log.error("Erreur lors de l'enregistrement de l'historique: {}", e.getMessage());
        }

        log.info("--- Fin de la synchronisation automatique ---");
    }
}