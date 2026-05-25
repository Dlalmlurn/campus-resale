package com.campusresale.platform.health;

import com.campusresale.platform.modules.ModuleDescriptor;
import java.time.Instant;
import java.util.List;

public record HealthResponse(
        String status,
        String service,
        Instant checkedAt,
        List<ModuleDescriptor> modules
) {
}
