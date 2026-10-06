package com.github.kevinldg.backend;

import com.github.kevinldg.backend.audit.AuditLogRepository;
import com.github.kevinldg.backend.maintenance.MaintenanceSettingsRepository;
import com.github.kevinldg.backend.setup.DataInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "spring.mongodb.uri=mongodb://localhost:27017/serverdashboard-test")
// The maintenance state would otherwise be read from MongoDB (unknown means "off"); audit entries are not stored.
@MockitoBean(types = {MaintenanceSettingsRepository.class, AuditLogRepository.class})
class BackendApplicationTests {

    // Would access MongoDB on startup
    @MockitoBean
    DataInitializer dataInitializer;

    @Test
    void contextLoads() {
    }

}
