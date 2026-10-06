package com.github.kevinldg.backend.role;

import com.github.kevinldg.backend.audit.AuditLogRepository;
import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.maintenance.MaintenanceSettingsRepository;
import com.github.kevinldg.backend.setup.DataInitializer;
import com.github.kevinldg.backend.support.CsrfSupport;
import com.github.kevinldg.backend.user.User;
import com.github.kevinldg.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.mongodb.uri=mongodb://localhost:27017/serverdashboard-test")
// The maintenance state would otherwise be read from MongoDB (unknown means "off"); audit entries are not stored.
@MockitoBean(types = {MaintenanceSettingsRepository.class, AuditLogRepository.class})
@AutoConfigureMockMvc
class RoleManagementControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RoleManagementService roleManagementService;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    RoleRepository roleRepository;

    @MockitoBean
    DataInitializer dataInitializer;

    @Test
    void roleManagementRequiresPermission() throws Exception {
        Permission[] allButRoleManage = EnumSet.complementOf(EnumSet.of(Permission.ROLE_MANAGE)).toArray(Permission[]::new);

        mockMvc.perform(get("/api/admin/roles").with(userWith(allButRoleManage)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/permissions").with(userWith(allButRoleManage)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(roleManagementService);

        when(roleManagementService.listPermissions()).thenReturn(List.of(
                new PermissionInfo(Permission.CONTAINER_START, Permission.Group.CONTAINERS, "Start containers")));
        mockMvc.perform(get("/api/admin/permissions").with(userWith(Permission.ROLE_MANAGE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("CONTAINER_START"))
                .andExpect(jsonPath("$[0].group").value("CONTAINERS"));
    }

    @Test
    void createRoleReturnsCreated() throws Exception {
        when(roleManagementService.createRole(any(), any())).thenReturn(
                new RoleResponse("r1", "Streamers", false, false, Set.of(Permission.CONTAINER_START), 0));

        mockMvc.perform(post("/api/admin/roles").with(userWith(Permission.ROLE_MANAGE)).with(CsrfSupport.xsrf(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Streamers", "permissions": ["CONTAINER_START"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Streamers"));

        verify(roleManagementService).createRole(eq(new RoleRequest("Streamers", Set.of(Permission.CONTAINER_START))), any());
    }

    @Test
    void unknownPermissionIsRejected() throws Exception {
        mockMvc.perform(post("/api/admin/roles").with(userWith(Permission.ROLE_MANAGE)).with(CsrfSupport.xsrf(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Streamers", "permissions": ["DO_EVERYTHING"]}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(roleManagementService);
    }

    @Test
    void invalidRoleNameIsRejected() throws Exception {
        mockMvc.perform(post("/api/admin/roles").with(userWith(Permission.ROLE_MANAGE)).with(CsrfSupport.xsrf(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "<b>x</b>", "permissions": []}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
        verifyNoInteractions(roleManagementService);
    }

    private RequestPostProcessor userWith(Permission... permissions) {
        Role role = new Role();
        role.setId("role-test");
        role.setName("Test");
        role.setPermissions(EnumSet.of(permissions[0], permissions));

        User user = new User();
        user.setId("actor-1");
        user.setUsername("actor");
        user.setRoleId(role.getId());
        user.setActive(true);
        when(userRepository.findById("actor-1")).thenReturn(Optional.of(user));
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));

        AuthenticatedUser principal = new AuthenticatedUser("actor-1", "actor", null, true, false, 0, Set.of());
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }
}
