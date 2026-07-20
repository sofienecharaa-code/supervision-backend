package com.supervision.supervisionbackend.service;

import com.supervision.supervisionbackend.config.ProxmoxProperties;
import com.supervision.supervisionbackend.model.Host;
import com.supervision.supervisionbackend.model.VirtualMachine;
import com.supervision.supervisionbackend.repository.HostRepository;
import com.supervision.supervisionbackend.repository.VirtualMachineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProxmoxSyncService {

    private final ProxmoxProperties proxmoxProperties;
    private final HostRepository hostRepository;
    private final VirtualMachineRepository vmRepository;
    private final RestTemplate proxmoxRestTemplate;

    public void syncAll() {
        if (!proxmoxProperties.isEnabled()) {
            log.info("Intégration Proxmox désactivée (proxmox.enabled=false) — synchronisation ignorée.");
            return;
        }

        try {
            List<String> nodeNames = syncNodes();
            for (String nodeName : nodeNames) {
                syncQemuVms(nodeName);
                syncLxcContainers(nodeName);
            }
        } catch (Exception e) {
            log.error("Échec de la synchronisation Proxmox: {}", e.getMessage());
            markKnownProxmoxHostsOffline();
        }
    }

    /**
     * Démarre ou arrête une VM/container Proxmox.
     * action doit être "start" ou "stop".
     */
    public void powerAction(VirtualMachine vm, Host host, String action) {
        String proxmoxType = "Container".equals(vm.getType()) ? "lxc" : "qemu";
        String url = proxmoxProperties.getHost() + "/api2/json/nodes/" + host.getName()
                + "/" + proxmoxType + "/" + vm.getExternalId() + "/status/" + action;

        HttpEntity<Void> request = new HttpEntity<>(authHeaders());
        proxmoxRestTemplate.exchange(url, HttpMethod.POST, request, String.class);

        log.info("Action Proxmox '{}' déclenchée sur {} ({})", action, vm.getName(), vm.getExternalId());
    }

    private void markKnownProxmoxHostsOffline() {
        hostRepository.findAll().stream()
                .filter(h -> "Proxmox".equals(h.getType()))
                .forEach(h -> {
                    boolean wasOnline = !"offline".equals(h.getStatus());

                    h.setStatus("offline");
                    h.setCpuUsage(0);
                    h.setRamUsage(0);
                    h.setStorageUsage(0);
                    hostRepository.save(h);

                    if (wasOnline) {
                        log.info("Host Proxmox '{}' marqué hors ligne (connexion échouée)", h.getName());
                    }

                    vmRepository.findByHostId(h.getId()).forEach(vm -> {
                        vm.setStatus("stopped");
                        vm.setCpuUsage(0);
                        vm.setRamUsage(0);
                        vmRepository.save(vm);
                    });
                });
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", proxmoxProperties.getToken());
        return headers;
    }

    private String extractHostIp() {
        try {
            URI uri = new URI(proxmoxProperties.getHost());
            return uri.getHost();
        } catch (Exception e) {
            return proxmoxProperties.getHost();
        }
    }

    private String resolveHostId(String nodeName) {
        return hostRepository.findByName(nodeName)
                .map(Host::getId)
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private List<String> syncNodes() {
        String url = proxmoxProperties.getHost() + "/api2/json/nodes";
        HttpEntity<Void> request = new HttpEntity<>(authHeaders());

        ResponseEntity<Map> response = proxmoxRestTemplate.exchange(
                url, HttpMethod.GET, request, Map.class
        );

        Map<String, Object> body = response.getBody();
        if (body == null) return List.of();

        List<Map<String, Object>> nodesData = (List<Map<String, Object>>) body.get("data");
        if (nodesData == null) return List.of();

        String realIp = extractHostIp();

        for (Map<String, Object> data : nodesData) {
            String nodeName = (String) data.get("node");

            double storagePercent = fetchNodeStorageUsage(nodeName);

            Host host = hostRepository.findByName(nodeName).orElseGet(Host::new);
            host.setName(nodeName);
            host.setType("Proxmox");
            host.setIpAddress(realIp);
            host.setStatus("online".equals(data.get("status")) ? "online" : mapNodeStatus((String) data.get("status")));
            host.setCpuUsage(toPercent(data.get("cpu")));
            host.setRamUsage(computeRamPercent(data));
            host.setStorageUsage(storagePercent);

            hostRepository.save(host);
        }

        log.info("Synchronisation Proxmox: {} nodes mis à jour", nodesData.size());
        return nodesData.stream().map(d -> (String) d.get("node")).toList();
    }

    @SuppressWarnings("unchecked")
    private double fetchNodeStorageUsage(String nodeName) {
        try {
            String url = proxmoxProperties.getHost() + "/api2/json/nodes/" + nodeName + "/status";
            HttpEntity<Void> request = new HttpEntity<>(authHeaders());

            ResponseEntity<Map> response = proxmoxRestTemplate.exchange(
                    url, HttpMethod.GET, request, Map.class
            );

            Map<String, Object> body = response.getBody();
            if (body == null) return 0;

            Map<String, Object> data = (Map<String, Object>) body.get("data");
            if (data == null) return 0;

            Map<String, Object> rootfs = (Map<String, Object>) data.get("rootfs");
            if (rootfs == null) return 0;

            Number used = (Number) rootfs.get("used");
            Number total = (Number) rootfs.get("total");
            if (used == null || total == null || total.doubleValue() == 0) return 0;

            return Math.round((used.doubleValue() / total.doubleValue()) * 100 * 10) / 10.0;
        } catch (Exception e) {
            log.warn("Impossible de récupérer le stockage pour le node {}: {}", nodeName, e.getMessage());
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private void syncQemuVms(String nodeName) {
        String url = proxmoxProperties.getHost() + "/api2/json/nodes/" + nodeName + "/qemu";
        HttpEntity<Void> request = new HttpEntity<>(authHeaders());

        ResponseEntity<Map> response = proxmoxRestTemplate.exchange(
                url, HttpMethod.GET, request, Map.class
        );

        Map<String, Object> body = response.getBody();
        if (body == null) return;

        List<Map<String, Object>> vmsData = (List<Map<String, Object>>) body.get("data");
        if (vmsData == null) return;

        String hostId = resolveHostId(nodeName);

        for (Map<String, Object> data : vmsData) {
            String vmName = (String) data.getOrDefault("name", "vm-" + data.get("vmid"));

            VirtualMachine vm = vmRepository.findByNameAndHostName(vmName, nodeName).orElseGet(VirtualMachine::new);
            vm.setName(vmName);
            vm.setExternalId(String.valueOf(data.get("vmid")));
            vm.setHostId(hostId);
            vm.setHostName(nodeName);
            vm.setType("VM");
            vm.setOs(vm.getOs() != null ? vm.getOs() : "Unknown");
            vm.setStatus(mapPowerState((String) data.get("status")));
            vm.setVcpuCount(((Number) data.getOrDefault("cpus", 0)).intValue());
            vm.setRamAllocatedGb(((Number) data.getOrDefault("maxmem", 0)).doubleValue() / (1024.0 * 1024 * 1024));
            vm.setCpuUsage(toPercent(data.get("cpu")));
            vm.setRamUsage(computeVmRamPercent(data));

            vmRepository.save(vm);
        }

        log.info("Synchronisation Proxmox: {} VMs QEMU mises à jour sur {}", vmsData.size(), nodeName);
    }

    @SuppressWarnings("unchecked")
    private void syncLxcContainers(String nodeName) {
        String url = proxmoxProperties.getHost() + "/api2/json/nodes/" + nodeName + "/lxc";
        HttpEntity<Void> request = new HttpEntity<>(authHeaders());

        ResponseEntity<Map> response = proxmoxRestTemplate.exchange(
                url, HttpMethod.GET, request, Map.class
        );

        Map<String, Object> body = response.getBody();
        if (body == null) return;

        List<Map<String, Object>> containersData = (List<Map<String, Object>>) body.get("data");
        if (containersData == null) return;

        String hostId = resolveHostId(nodeName);

        for (Map<String, Object> data : containersData) {
            String ctName = (String) data.getOrDefault("name", "ct-" + data.get("vmid"));

            VirtualMachine container = vmRepository.findByNameAndHostName(ctName, nodeName).orElseGet(VirtualMachine::new);
            container.setName(ctName);
            container.setExternalId(String.valueOf(data.get("vmid")));
            container.setHostId(hostId);
            container.setHostName(nodeName);
            container.setType("Container");
            container.setOs(container.getOs() != null ? container.getOs() : "Unknown");
            container.setStatus(mapPowerState((String) data.get("status")));
            container.setVcpuCount(((Number) data.getOrDefault("cpus", 0)).intValue());
            container.setRamAllocatedGb(((Number) data.getOrDefault("maxmem", 0)).doubleValue() / (1024.0 * 1024 * 1024));
            container.setCpuUsage(toPercent(data.get("cpu")));
            container.setRamUsage(computeVmRamPercent(data));

            vmRepository.save(container);
        }

        log.info("Synchronisation Proxmox: {} containers LXC mis à jour sur {}", containersData.size(), nodeName);
    }

    private double toPercent(Object rawCpuValue) {
        if (rawCpuValue == null) return 0;
        return Math.round(((Number) rawCpuValue).doubleValue() * 100 * 10) / 10.0;
    }

    private double computeRamPercent(Map<String, Object> data) {
        Number mem = (Number) data.get("mem");
        Number maxmem = (Number) data.get("maxmem");
        if (mem == null || maxmem == null || maxmem.doubleValue() == 0) return 0;
        return Math.round((mem.doubleValue() / maxmem.doubleValue()) * 100 * 10) / 10.0;
    }

    private double computeVmRamPercent(Map<String, Object> data) {
        return computeRamPercent(data);
    }

    private String mapNodeStatus(String proxmoxStatus) {
        if (proxmoxStatus == null) return "unknown";
        return switch (proxmoxStatus) {
            case "online" -> "online";
            case "offline" -> "offline";
            default -> "error";
        };
    }

    private String mapPowerState(String proxmoxStatus) {
        if (proxmoxStatus == null) return "unknown";
        return switch (proxmoxStatus) {
            case "running" -> "running";
            case "stopped" -> "stopped";
            default -> "error";
        };
    }
}