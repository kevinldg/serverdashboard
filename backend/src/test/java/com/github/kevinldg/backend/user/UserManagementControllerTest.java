package com.github.kevinldg.backend.user;

import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.maintenance.MaintenanceSettingsRepository;
import com.github.kevinldg.backend.role.Permission;
import com.github.kevinldg.backend.role.Role;
import com.github.kevinldg.backend.role.RoleRepository;
import com.github.kevinldg.backend.setup.DataInitializer;
import com.github.kevinldg.backend.support.CsrfSupport;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.mongodb.uri=mongodb://localhost:27017/serverdashboard-test")
// The maintenance state would otherwise be read from MongoDB; unknown means "off".
@MockitoBean(types = MaintenanceSettingsRepository.class)
@AutoConfigureMockMvc
class UserManagementControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserManagementService userManagementService;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    RoleRepository roleRepository;

    @MockitoBean
    DataInitializer dataInitializer;

    @Test
    void userManagementRequiresPermission() throws Exception {
        Permission[] allButUserManage = EnumSet.complementOf(EnumSet.of(Permission.USER_MANAGE)).toArray(Permission[]::new);

        mockMvc.perform(get("/api/admin/users").with(userWith(allButUserManage)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/users/u1").with(userWith(allButUserManage)).with(CsrfSupport.xsrf(mockMvc)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userManagementService);

        when(userManagementService.listUsers()).thenReturn(List.of());
        mockMvc.perform(get("/api/admin/users").with(userWith(Permission.USER_MANAGE)))
                .andExpect(status().isOk());
    }

    @Test
    void createUserReturnsCreatedWithGeneratedPassword() throws Exception {
        when(userManagementService.createUser(any(), any())).thenReturn(new CreatedUserResponse(
                new UserResponse("u1", "alice", new UserResponse.RoleSummary("r1", "User"), false, true, true, null, null),
                "GeneratedPassword2345"));

        mockMvc.perform(post("/api/admin/users").with(userWith(Permission.USER_MANAGE)).with(CsrfSupport.xsrf(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "alice", "roleId": "r1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username").value("alice"))
                .andExpect(jsonPath("$.generatedPassword").value("GeneratedPassword2345"));

        verify(userManagementService).createUser(eq(new CreateUserRequest("alice", "r1", null)), any());
    }

    @Test
    void invalidUsernameIsRejected() throws Exception {
        mockMvc.perform(post("/api/admin/users").with(userWith(Permission.USER_MANAGE)).with(CsrfSupport.xsrf(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "al ice<script>", "roleId": "r1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists());
        verifyNoInteractions(userManagementService);
    }

    @Test
    void stateChangingRequestsRequireCsrfToken() throws Exception {
        mockMvc.perform(post("/api/admin/users/u1/reset-password").with(userWith(Permission.USER_MANAGE)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userManagementService);
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
