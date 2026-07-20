package com.supervision.supervisionbackend.repository;

import com.supervision.supervisionbackend.model.Host;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface HostRepository extends MongoRepository<Host, String> {
    Optional<Host> findByName(String name);
}