package com.campusresale.platform.modules;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ModuleRegistry {

    private static final List<ModuleDescriptor> MODULES = List.of(
            new ModuleDescriptor("M01", "identity", "com.campusresale.identity", "PLANNED"),
            new ModuleDescriptor("M02", "goods", "com.campusresale.goods", "PLANNED"),
            new ModuleDescriptor("M03", "intelligence", "com.campusresale.intelligence", "PLANNED"),
            new ModuleDescriptor("M04", "discovery", "com.campusresale.discovery", "PLANNED"),
            new ModuleDescriptor("M05", "conversation", "com.campusresale.conversation", "IN_PROGRESS"),
            new ModuleDescriptor("M06", "order", "com.campusresale.order", "PLANNED"),
            new ModuleDescriptor("M07", "payment", "com.campusresale.payment", "PLANNED"),
            new ModuleDescriptor("M08", "reputation", "com.campusresale.reputation", "PLANNED"),
            new ModuleDescriptor("M09", "governance", "com.campusresale.governance", "PLANNED"),
            new ModuleDescriptor("M10", "notification", "com.campusresale.notification", "PLANNED")
    );

    public List<ModuleDescriptor> modules() {
        return MODULES;
    }
}
