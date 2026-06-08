package com.campusresale.platform.modules;

public record ModuleDescriptor(
        String code,
        String name,
        String packageName,
        String status
) {
}
