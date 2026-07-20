package com.supervision.supervisionbackend.controller;

import com.supervision.supervisionbackend.model.MetricHistory;
import com.supervision.supervisionbackend.service.MetricHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/history")
@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
public class MetricHistoryController {

    private final MetricHistoryService historyService;

    @GetMapping("/{hostId}")
    public List<MetricHistory> getHistory(@PathVariable String hostId) {
        return historyService.getHistoryForHost(hostId);
    }
}