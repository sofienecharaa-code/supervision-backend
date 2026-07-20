package com.supervision.supervisionbackend.controller;

import com.supervision.supervisionbackend.model.VirtualMachine;
import com.supervision.supervisionbackend.repository.VirtualMachineRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vms")
@CrossOrigin(origins = "http://localhost:4200")
public class VirtualMachineController {

    @Autowired
    private VirtualMachineRepository vmRepository;

    @GetMapping
    public List<VirtualMachine> getAllVms() {
        return vmRepository.findAll();
    }

    @GetMapping("/host/{hostId}")
    public List<VirtualMachine> getVmsByHost(@PathVariable String hostId) {
        return vmRepository.findByHostId(hostId);
    }

    @PostMapping
    public VirtualMachine createVm(@RequestBody VirtualMachine vm) {
        return vmRepository.save(vm);
    }

    @DeleteMapping("/{id}")
    public void deleteVm(@PathVariable String id) {
        vmRepository.deleteById(id);
    }
}