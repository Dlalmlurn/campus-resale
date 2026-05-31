package com.campusresale.platform.health;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusresale.identity.application.SessionLookupService;
import com.campusresale.platform.modules.ModuleRegistry;
import com.campusresale.platform.security.AuthorizationInterceptor;
import com.campusresale.platform.security.OriginCsrfInterceptor;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
@Import(ModuleRegistry.class)
@TestPropertySource(properties = "spring.application.name=campus-resale-api-test")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OriginCsrfInterceptor originCsrfInterceptor;

    @MockBean
    private AuthorizationInterceptor authorizationInterceptor;

    @MockBean
    private SessionLookupService sessionLookupService;

    @BeforeEach
    void allowSecurityInfrastructureByDefault() throws Exception {
        when(originCsrfInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(authorizationInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(sessionLookupService.loadByRawToken(nullable(String.class))).thenReturn(Optional.empty());
    }

    @Test
    void returnsApiHealthAndModuleMap() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("campus-resale-api-test"))
                .andExpect(jsonPath("$.modules", hasSize(10)));
    }
}
