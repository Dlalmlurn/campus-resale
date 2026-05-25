package com.campusresale.platform.modules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModuleRegistryTest {

    private final ModuleRegistry registry = new ModuleRegistry();

    @Test
    void exposesProjectShapeModules() {
        assertThat(registry.modules())
                .extracting(ModuleDescriptor::code)
                .containsExactly("M01", "M02", "M03", "M04", "M05", "M06", "M07", "M08", "M09", "M10");
    }
}
