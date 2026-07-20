package com.supervision.supervisionbackend.controller;

import com.supervision.supervisionbackend.model.Host;
import com.supervision.supervisionbackend.repository.HostRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hosts")
@CrossOrigin(origins = "http://localhost:4200")
public class HostController {

    @Autowired
    private HostRepository hostRepository;

    @GetMapping
    public List<Host> getAllHosts() {
        return hostRepository.findAll();
    }

    @PostMapping
    public Host createHost(@RequestBody Host host) {
        return hostRepository.save(host);
    }

    @DeleteMapping("/{id}")
    public void deleteHost(@PathVariable String id) {
        hostRepository.deleteById(id);
    }
}