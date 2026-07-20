package com.supervision.supervisionbackend.controller;

import com.supervision.supervisionbackend.model.Host;
import com.supervision.supervisionbackend.model.VirtualMachine;
import com.supervision.supervisionbackend.repository.HostRepository;
import com.supervision.supervisionbackend.repository.VirtualMachineRepository;
import com.supervision.supervisionbackend.service.ProxmoxSyncService;
import com.supervision.supervisionbackend.service.VMwareSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vms")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class VmActionController {

    private final VirtualMachineRepository vmRepository;
    private final HostRepository hostRepository;
    private final ProxmoxSyncService proxmoxSyncService;
    private final VMwareSyncService vmwareSyncService;

    @PostMapping("/{id}/power")
    public ResponseEntity<String> powerAction(@PathVariable String id, @RequestParam String action) {
        if (!"start".equals(action) && !"stop".equals(action)) {
            return ResponseEntity.badRequest().body("action doit être 'start' ou 'stop'");
        }

        VirtualMachine vm = vmRepository.findById(id).orElse(null);
        if (vm == null) {
            return ResponseEntity.notFound().build();
        }

        Host host = hostRepository.findById(vm.getHostId()).orElse(null);
        if (host == null) {
            return ResponseEntity.badRequest().body("Host introuvable pour cette VM");
        }

        try {
            if ("Proxmox".equals(host.getType())) {
                proxmoxSyncService.powerAction(vm, host, action);
            } else if ("VMware".equals(host.getType())) {
                vmwareSyncService.powerAction(vm, action);
            } else {
                return ResponseEntity.badRequest().body("Type de host non supporté");
            }
            return ResponseEntity.ok("Action '" + action + "' envoyée pour " + vm.getName());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erreur: " + e.getMessage());
        }
    }
}