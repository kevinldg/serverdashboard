package com.github.kevinldg.backend.container.creation;

import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.maintenance.MaintenanceSettingsRepository;
import com.github.kevinldg.backend.role.Permission;
import com.github.kevinldg.backend.role.Role;
import com.github.kevinldg.backend.role.RoleRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.mongodb.uri=mongodb://localhost:27017/serverdashboard-test")
@MockitoBean(types = MaintenanceSettingsRepository.class)
@AutoConfigureMockMvc
class ContainerCreationControllerTest {

    private static final String VALID_REQUEST = """
            {"name": "web", "image": "nginx:1.27", "ports": [{"hostPort": 8080, "containerPort": 80, "protocol": "TCP"}],
             "mounts": [], "environment": [], "restartPolicy": {"name": "unless-stopped"}, "network": "bridge",
             "start": true}
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ContainerCreationService creationService;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    RoleRepository roleRepository;

    @MockitoBean
    DataInitializer dataInitializer;

    @Test
    void creationRequiresPermission() throws Exception {
        Permission[] allOthers = EnumSet.complementOf(EnumSet.of(Permission.CONTAINER_CREATE)).toArray(Permission[]::new);

        mockMvc.perform(get("/api/containers/creation-options").with(userWith(allOthers)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/containers").with(userWith(allOthers)).with(CsrfSupport.xsrf(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/container-jobs/j1").with(userWith(allOthers)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(creationService);
    }

    @Test
    void createReturnsAcceptedJob() throws Exception {
        when(creationService.create(any(), any())).thenReturn(new CreationJobResponse("j1", CreationJobStatus.CREATING,
                "Preparing…", null, "web", null, null, null));

        mockMvc.perform(post("/api/containers").with(userWith(Permission.CONTAINER_CREATE)).with(CsrfSupport.xsrf(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value("j1"));
    }

    @Test
    void invalidFieldsAreRejected() throws Exception {
        mockMvc.perform(post("/api/containers").with(userWith(Permission.CONTAINER_CREATE)).with(CsrfSupport.xsrf(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("\"web\"", "\"-bad name\"").replace("8080", "70000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
        verifyNoInteractions(creationService);
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
