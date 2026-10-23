package com.example.demo;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    // Injected at image build time (git short SHA) so the running pod proves which commit it was built from.
    @Value("${app.version:dev}")
    private String version;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "demo-app", "version", version);
    }
}
