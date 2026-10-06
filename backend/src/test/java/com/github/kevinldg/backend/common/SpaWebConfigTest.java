package com.github.kevinldg.backend.common;

import com.github.kevinldg.backend.audit.AuditLogRepository;
import com.github.kevinldg.backend.maintenance.MaintenanceSettingsRepository;
import com.github.kevinldg.backend.setup.DataInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uses the placeholder frontend in src/test/resources/static. */
@SpringBootTest(properties = "spring.mongodb.uri=mongodb://localhost:27017/serverdashboard-test")
@MockitoBean(types = {MaintenanceSettingsRepository.class, AuditLogRepository.class})
@AutoConfigureMockMvc
class SpaWebConfigTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    DataInitializer dataInitializer;

    @Test
    void frontendRoutesReturnIndexHtml() throws Exception {
        // "/" is handled by Spring Boot's welcome page, which forwards to index.html (MockMvc does not follow forwards)
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
        mockMvc.perform(get("/containers/abc"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("test frontend")));
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("test frontend")));
    }

    @Test
    void apiPathsNeverReturnTheFrontend() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(not(containsString("test frontend"))));
    }
}
