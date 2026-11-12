package com.tejas.photography.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Used by Render / Railway health checks and to wake the service up (free tiers sleep). */
@RestController
public class HealthController {

    @GetMapping({"/", "/api/health"})
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "tejas-photography-backend");
    }
}
