package com.supervision.supervisionbackend.controller;

import com.supervision.supervisionbackend.service.ProxmoxSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/proxmox")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class ProxmoxSyncController {

    private final ProxmoxSyncService syncService;

    @PostMapping("/sync")
    public String triggerSync() {
        syncService.syncAll();
        return "Synchronisation Proxmox déclenchée (voir les logs pour le détail)";
    }
}