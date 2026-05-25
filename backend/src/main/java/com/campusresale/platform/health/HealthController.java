package com.campusresale.platform.health;

import com.campusresale.platform.modules.ModuleRegistry;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final String applicationName;
    private final ModuleRegistry moduleRegistry;

    public HealthController(
            @Value("${spring.application.name}") String applicationName,
            ModuleRegistry moduleRegistry
    ) {
        this.applicationName = applicationName;
        this.moduleRegistry = moduleRegistry;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse(
                "UP",
                applicationName,
                Instant.now(),
                moduleRegistry.modules()
        );
    }
}
