package com.github.kevinldg.backend.maintenance;

import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.role.Permission;
import com.github.kevinldg.backend.role.Role;
import com.github.kevinldg.backend.role.RoleRepository;
import com.github.kevinldg.backend.setup.DataInitializer;
import com.github.kevinldg.backend.support.CsrfSupport;
import com.github.kevinldg.backend.user.User;
import com.github.kevinldg.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.mongodb.uri=mongodb://localhost:27017/serverdashboard-test")
@AutoConfigureMockMvc
class MaintenanceModeIntegrationTest {

    private static final String PASSWORD = "correct-password-123";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    MaintenanceService maintenanceService;

    @MockitoBean
    MaintenanceSettingsRepository maintenanceSettingsRepository;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    RoleRepository roleRepository;

    @MockitoBean
    DataInitializer dataInitializer;

    private final AuthenticatedUser systemAdmin = new AuthenticatedUser("x", "system", null, true, true, 0, Set.of());

    @BeforeEach
    void setUp() {
        Role adminRole = role("role-admin", "Admin", true, Set.of());
        Role userRole = role("role-user", "User", false, EnumSet.of(Permission.DASHBOARD_VIEW, Permission.MAINTENANCE_MANAGE));
        stubUser("admin-1", "kevin", adminRole);
        stubUser("user-1", "alice", userRole);
        when(maintenanceSettingsRepository.save(any(MaintenanceSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // The service is shared by all tests of this class; start each test with maintenance off.
        maintenanceService.update(new MaintenanceRequest(false, ""), systemAdmin);
    }

    @Test
    void publicStatusIsAvailableWithoutLogin() throws Exception {
        enableMaintenance();

        mockMvc.perform(get("/api/maintenance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.message").value("Back at 22:00."))
                .andExpect(jsonPath("$.updatedBy").doesNotExist());
    }

    @Test
    void nonAdminLoginIsRejectedDuringMaintenance() throws Exception {
        enableMaintenance();

        MvcResult result = mockMvc.perform(post("/api/auth/login").with(CsrfSupport.xsrf(mockMvc))
                        .param("username", "alice")
                        .param("password", PASSWORD))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.maintenance").value(true))
                .andExpect(jsonPath("$.maintenanceMessage").value("Back at 22:00."))
                .andReturn();

        // No usable session was created
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        if (session != null) {
            assertThat(session.isInvalid()).isTrue();
        }
    }

    @Test
    void adminsCanLogInAndUseTheApplication() throws Exception {
        enableMaintenance();

        MockHttpSession session = login("kevin");
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(true));
    }

    @Test
    void existingNonAdminSessionsAreBlockedButCanLogOut() throws Exception {
        MockHttpSession session = login("alice");
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());

        enableMaintenance();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.maintenance").value(true));
        // Even with MAINTENANCE_MANAGE, non-admins cannot use the API during maintenance
        mockMvc.perform(put("/api/admin/maintenance").session(session).with(CsrfSupport.xsrf(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled": false, "message": ""}
                                """))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/api/maintenance").session(session)).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/logout").session(session).with(CsrfSupport.xsrf(mockMvc)))
                .andExpect(status().isNoContent());
    }

    @Test
    void maintenanceSettingsRequirePermission() throws Exception {
        MockHttpSession session = login("alice");
        // alice's role has MAINTENANCE_MANAGE; remove it
        stubUser("user-1", "alice", role("role-user", "User", false, EnumSet.of(Permission.DASHBOARD_VIEW)));

        mockMvc.perform(get("/api/admin/maintenance").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanEnableAndDisableMaintenance() throws Exception {
        MockHttpSession session = login("kevin");

        mockMvc.perform(put("/api/admin/maintenance").session(session).with(CsrfSupport.xsrf(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled": true, "message": "Upgrading Docker"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.updatedBy").value("kevin"));
        assertThat(maintenanceService.isEnabled()).isTrue();

        mockMvc.perform(put("/api/admin/maintenance").session(session).with(CsrfSupport.xsrf(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled": false}
                                """))
                .andExpect(status().isOk());
        assertThat(maintenanceService.isEnabled()).isFalse();
    }

    private void enableMaintenance() {
        maintenanceService.update(new MaintenanceRequest(true, "Back at 22:00."), systemAdmin);
    }

    private MockHttpSession login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(CsrfSupport.xsrf(mockMvc))
                        .param("username", username)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void stubUser(String id, String username, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRoleId(role.getId());
        user.setActive(true);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));
    }

    private static Role role(String id, String name, boolean superuser, Set<Permission> permissions) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        role.setSuperuser(superuser);
        role.setPermissions(EnumSet.noneOf(Permission.class));
        role.getPermissions().addAll(permissions);
        return role;
    }
}
