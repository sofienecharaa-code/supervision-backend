package com.supervision.supervisionbackend.controller;

import com.supervision.supervisionbackend.service.VMwareSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vmware")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class VMwareSyncController {

    private final VMwareSyncService syncService;

    @PostMapping("/sync")
    public String triggerSync() {
        syncService.syncAll();
        return "Synchronisation VMware déclenchée (voir les logs pour le détail)";
    }
}