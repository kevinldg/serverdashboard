package com.github.kevinldg.backend.audit;

import com.github.kevinldg.backend.audit.AuditService.AuditLogFilter;
import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.maintenance.MaintenanceSettingsRepository;
import com.github.kevinldg.backend.role.Permission;
import com.github.kevinldg.backend.role.Role;
import com.github.kevinldg.backend.role.RoleRepository;
import com.github.kevinldg.backend.setup.DataInitializer;
import com.github.kevinldg.backend.user.User;
import com.github.kevinldg.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.mongodb.uri=mongodb://localhost:27017/serverdashboard-test")
// The maintenance state would otherwise be read from MongoDB; unknown means "off".
@MockitoBean(types = MaintenanceSettingsRepository.class)
@AutoConfigureMockMvc
class AuditLogControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuditService auditService;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    RoleRepository roleRepository;

    @MockitoBean
    DataInitializer dataInitializer;

    @Test
    void requiresAuditLogPermissionAndRecordsDeniedAccess() throws Exception {
        Permission[] allButAudit = EnumSet.complementOf(EnumSet.of(Permission.AUDIT_LOG_VIEW)).toArray(Permission[]::new);

        mockMvc.perform(get("/api/admin/audit-log").with(userWith(allButAudit)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/audit-log/actors").with(userWith(allButAudit)))
                .andExpect(status().isForbidden());

        verify(auditService, never()).search(any(), anyInt(), anyInt());
        verify(auditService).recordDeduplicated(argThat(event -> event.action() == AuditAction.ACCESS_DENIED
                && event.outcome() == AuditOutcome.DENIED
                && event.actor().equals("tester")
                && event.target().equals("GET /api/admin/audit-log")));
    }

    @Test
    void returnsFilteredPage() throws Exception {
        AuditEntryResponse entry = new AuditEntryResponse("e1", Instant.parse("2026-10-05T12:00:00Z"), "kevin",
                AuditCategory.CONTAINER, AuditAction.CONTAINER_STOP, AuditOutcome.SUCCESS, "minecraft",
                "Stopped container 'minecraft'", Map.of(), null);
        AuditLogFilter expectedFilter = new AuditLogFilter(AuditCategory.CONTAINER, "kevin", AuditOutcome.SUCCESS,
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-06T00:00:00Z"));
        when(auditService.search(expectedFilter, 2, 20)).thenReturn(new AuditLogPage(List.of(entry), 2, 20, 41, 3));

        mockMvc.perform(get("/api/admin/audit-log").with(userWith(Permission.AUDIT_LOG_VIEW))
                        .param("page", "2").param("size", "20")
                        .param("category", "CONTAINER").param("actor", "kevin").param("outcome", "SUCCESS")
                        .param("from", "2026-10-01T00:00:00Z").param("to", "2026-10-06T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].summary").value("Stopped container 'minecraft'"))
                .andExpect(jsonPath("$.entries[0].action").value("CONTAINER_STOP"))
                .andExpect(jsonPath("$.totalElements").value(41))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void listsActors() throws Exception {
        when(auditService.listActors()).thenReturn(List.of("alice", "kevin"));

        mockMvc.perform(get("/api/admin/audit-log/actors").with(userWith(Permission.AUDIT_LOG_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1]").value("kevin"));
    }

    @Test
    void rejectsInvalidParameters() throws Exception {
        mockMvc.perform(get("/api/admin/audit-log").with(userWith(Permission.AUDIT_LOG_VIEW)).param("size", "500"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/audit-log").with(userWith(Permission.AUDIT_LOG_VIEW)).param("category", "NOPE"))
                .andExpect(status().isBadRequest());
        verify(auditService, never()).search(any(), anyInt(), anyInt());
    }

    /**
     * Simulates a logged-in session. The user is reloaded from the (mocked) database on each request,
     * so the role's permissions are what counts.
     */
    private RequestPostProcessor userWith(Permission... permissions) {
        Role role = new Role();
        role.setId("role-1");
        role.setName("Custom");
        role.setPermissions(Set.of(permissions));

        User user = new User();
        user.setId("user-1");
        user.setUsername("tester");
        user.setRoleId(role.getId());
        user.setActive(true);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));

        AuthenticatedUser principal = new AuthenticatedUser("user-1", "tester", null, true, false, 0, Set.of());
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }
}
