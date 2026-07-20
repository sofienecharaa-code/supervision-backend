package com.supervision.supervisionbackend.repository;

import com.supervision.supervisionbackend.model.VirtualMachine;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface VirtualMachineRepository extends MongoRepository<VirtualMachine, String> {
    List<VirtualMachine> findByHostId(String hostId);
    Optional<VirtualMachine> findByNameAndHostName(String name, String hostName);
}