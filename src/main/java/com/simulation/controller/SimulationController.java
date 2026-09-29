package com.simulation.controller;

import com.simulation.service.SimulationEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    private final SimulationEngine simulationEngine;

    public SimulationController(SimulationEngine simulationEngine) {
        this.simulationEngine = simulationEngine;
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return Map.of(
                "status", "ONLINE",
                "currentTick", simulationEngine.getCurrentTick(),
                "virtualThreadsEnabled", Thread.currentThread().isVirtual()
        );
    }
}
