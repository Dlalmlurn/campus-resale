package com.campusresale.platform.health;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusresale.platform.modules.ModuleRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
@Import(ModuleRegistry.class)
@TestPropertySource(properties = "spring.application.name=campus-resale-api-test")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsApiHealthAndModuleMap() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("campus-resale-api-test"))
                .andExpect(jsonPath("$.modules", hasSize(10)));
    }
}
