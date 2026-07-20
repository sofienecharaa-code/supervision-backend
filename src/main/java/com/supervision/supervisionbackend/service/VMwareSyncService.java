package com.supervision.supervisionbackend.service;

import com.supervision.supervisionbackend.config.VMwareProperties;
import com.supervision.supervisionbackend.model.Host;
import com.supervision.supervisionbackend.model.VirtualMachine;
import com.supervision.supervisionbackend.repository.HostRepository;
import com.supervision.supervisionbackend.repository.VirtualMachineRepository;
import com.vmware.vim25.HostListSummary;
import com.vmware.vim25.ManagedObjectReference;
import com.vmware.vim25.VirtualMachineSummary;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ServiceInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URL;

@Service
@RequiredArgsConstructor
@Slf4j
public class VMwareSyncService {

    private final VMwareProperties vmwareProperties;
    private final HostRepository hostRepository;
    private final VirtualMachineRepository vmRepository;

    public void syncAll() {
        if (!vmwareProperties.isEnabled()) {
            log.info("Intégration VMware désactivée (vmware.enabled=false) — synchronisation ignorée.");
            return;
        }

        ServiceInstance si = null;
        try {
            String url = vmwareProperties.getHost() + "/sdk";
            si = new ServiceInstance(new URL(url), vmwareProperties.getUsername(), vmwareProperties.getPassword(), true);

            log.info("Connexion VMware réussie via VIM API: {}", si.getAboutInfo().getFullName());

            String hostName = syncHosts(si);
            syncVirtualMachines(si, hostName);

        } catch (Exception e) {
            log.error("Échec de la synchronisation VMware: {}", e.getMessage());
            markKnownVMwareHostsOffline();
        } finally {
            if (si != null) {
                try {
                    si.getServerConnection().logout();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Démarre ou arrête une VM VMware via l'API VIM.
     * action doit être "start" ou "stop".
     */
    public void powerAction(VirtualMachine vm, String action) throws Exception {
        String url = vmwareProperties.getHost() + "/sdk";
        ServiceInstance si = new ServiceInstance(new URL(url), vmwareProperties.getUsername(), vmwareProperties.getPassword(), true);

        try {
            ManagedObjectReference mor = new ManagedObjectReference();
            mor.setType("VirtualMachine");
            mor.setVal(vm.getExternalId());

            com.vmware.vim25.mo.VirtualMachine vmEntity =
                    new com.vmware.vim25.mo.VirtualMachine(si.getServerConnection(), mor);

            if ("start".equals(action)) {
                vmEntity.powerOnVM_Task(null);
            } else {
                vmEntity.powerOffVM_Task();
            }

            log.info("Action VMware '{}' déclenchée sur {}", action, vm.getName());
        } finally {
            si.getServerConnection().logout();
        }
    }

    private void markKnownVMwareHostsOffline() {
        hostRepository.findAll().stream()
                .filter(h -> "VMware".equals(h.getType()))
                .forEach(h -> {
                    boolean wasOnline = !"offline".equals(h.getStatus());

                    h.setStatus("offline");
                    h.setCpuUsage(0);
                    h.setRamUsage(0);
                    h.setStorageUsage(0);
                    hostRepository.save(h);

                    if (wasOnline) {
                        log.info("Host VMware '{}' marqué hors ligne (connexion échouée)", h.getName());
                    }

                    vmRepository.findByHostId(h.getId()).forEach(vm -> {
                        vm.setStatus("stopped");
                        vm.setCpuUsage(0);
                        vm.setRamUsage(0);
                        vmRepository.save(vm);
                    });
                });
    }

    private String syncHosts(ServiceInstance si) throws Exception {
        ManagedEntity[] entities = new InventoryNavigator(si.getRootFolder())
                .searchManagedEntities("HostSystem");

        String firstHostName = null;

        for (ManagedEntity entity : entities) {
            HostSystem hostSystem = (HostSystem) entity;
            HostListSummary summary = hostSystem.getSummary();

            String hostName = hostSystem.getName();
            if (firstHostName == null) {
                firstHostName = hostName;
            }

            Host host = hostRepository.findByName(hostName).orElseGet(Host::new);
            host.setName(hostName);
            host.setType("VMware");
            host.setIpAddress(vmwareProperties.getHost().replace("https://", ""));
            host.setStatus(mapHostStatus(summary.getRuntime().getConnectionState().toString()));

            long totalMemMb = summary.getHardware().getMemorySize() / (1024 * 1024);
            long usedMemMb = summary.getQuickStats().getOverallMemoryUsage() != null
                    ? summary.getQuickStats().getOverallMemoryUsage() : 0;
            double ramPercent = totalMemMb > 0 ? Math.round((usedMemMb * 100.0 / totalMemMb) * 10) / 10.0 : 0;

            int numCpuCores = summary.getHardware().getNumCpuCores();
            Integer cpuUsageMhz = summary.getQuickStats().getOverallCpuUsage();
            int cpuMhzPerCore = summary.getHardware().getCpuMhz();
            double totalCpuMhz = (double) numCpuCores * cpuMhzPerCore;
            double cpuPercent = (cpuUsageMhz != null && totalCpuMhz > 0)
                    ? Math.round((cpuUsageMhz / totalCpuMhz) * 100 * 10) / 10.0 : 0;

            host.setRamUsage(ramPercent);
            host.setCpuUsage(cpuPercent);
            host.setStorageUsage(0);

            hostRepository.save(host);
        }

        log.info("Synchronisation VMware: {} host(s) mis à jour", entities.length);
        return firstHostName;
    }

    private void syncVirtualMachines(ServiceInstance si, String hostName) throws Exception {
        ManagedEntity[] entities = new InventoryNavigator(si.getRootFolder())
                .searchManagedEntities("VirtualMachine");

        String hostId = hostRepository.findByName(hostName).map(Host::getId).orElse(null);

        for (ManagedEntity entity : entities) {
            com.vmware.vim25.mo.VirtualMachine vmEntity = (com.vmware.vim25.mo.VirtualMachine) entity;
            VirtualMachineSummary summary = vmEntity.getSummary();

            String vmName = vmEntity.getName();

            VirtualMachine vm = vmRepository.findByNameAndHostName(vmName, hostName).orElseGet(VirtualMachine::new);
            vm.setName(vmName);
            vm.setExternalId(vmEntity.getMOR().getVal());
            vm.setHostId(hostId);
            vm.setHostName(hostName);
            vm.setType("VM");
            vm.setOs(summary.getConfig().getGuestFullName() != null ? summary.getConfig().getGuestFullName() : "Unknown");
            vm.setStatus(mapPowerState(summary.getRuntime().getPowerState().toString()));
            vm.setVcpuCount(summary.getConfig().getNumCpu());
            vm.setRamAllocatedGb(summary.getConfig().getMemorySizeMB() / 1024.0);

            Integer cpuUsageMhz = summary.getQuickStats().getOverallCpuUsage();
            Integer ramUsageMb = summary.getQuickStats().getGuestMemoryUsage();
            double ramPercent = ramUsageMb != null && summary.getConfig().getMemorySizeMB() > 0
                    ? Math.round((ramUsageMb * 100.0 / summary.getConfig().getMemorySizeMB()) * 10) / 10.0 : 0;

            vm.setRamUsage(ramPercent);
            vm.setCpuUsage(cpuUsageMhz != null ? cpuUsageMhz : 0);

            vmRepository.save(vm);
        }

        log.info("Synchronisation VMware: {} VM(s) mise(s) à jour", entities.length);
    }

    private String mapHostStatus(String connectionState) {
        return switch (connectionState) {
            case "connected" -> "online";
            case "disconnected", "notResponding" -> "offline";
            default -> "error";
        };
    }

    private String mapPowerState(String powerState) {
        return switch (powerState) {
            case "poweredOn" -> "running";
            case "poweredOff" -> "stopped";
            case "suspended" -> "suspended";
            default -> "error";
        };
    }
}