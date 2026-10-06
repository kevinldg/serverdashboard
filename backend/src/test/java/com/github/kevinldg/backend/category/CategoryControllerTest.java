package com.github.kevinldg.backend.category;

import com.github.kevinldg.backend.audit.AuditLogRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.mongodb.uri=mongodb://localhost:27017/serverdashboard-test")
// The maintenance state would otherwise be read from MongoDB (unknown means "off"); audit entries are not stored.
@MockitoBean(types = {MaintenanceSettingsRepository.class, AuditLogRepository.class})
@AutoConfigureMockMvc
class CategoryControllerTest {

    private static final String BODY = """
            {"name": "System", "color": "GRAY"}
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CategoryService categoryService;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    RoleRepository roleRepository;

    @MockitoBean
    DataInitializer dataInitializer;

    @Test
    void managementRequiresCategoryPermission() throws Exception {
        Permission[] allOthers = EnumSet.complementOf(EnumSet.of(Permission.CATEGORY_MANAGE)).toArray(Permission[]::new);

        mockMvc.perform(get("/api/admin/categories").with(userWith(allOthers)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/categories").with(userWith(allOthers)).with(CsrfSupport.xsrf(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/categories/1").with(userWith(allOthers)).with(CsrfSupport.xsrf(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/categories/1").with(userWith(allOthers)).with(CsrfSupport.xsrf(mockMvc)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(categoryService);
    }

    @Test
    void createsCategory() throws Exception {
        when(categoryService.create(eq(new CategoryRequest("System", CategoryColor.GRAY)), any()))
                .thenReturn(new CategoryResponse("1", "System", CategoryColor.GRAY, 0));

        mockMvc.perform(post("/api/admin/categories").with(userWith(Permission.CATEGORY_MANAGE))
                        .with(CsrfSupport.xsrf(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.color").value("GRAY"));
    }

    @Test
    void rejectsInvalidRequests() throws Exception {
        for (String body : List.of("{\"name\": \" \", \"color\": \"GRAY\"}",
                "{\"name\": \"" + "x".repeat(31) + "\", \"color\": \"GRAY\"}",
                "{\"name\": \"System\"}",
                "{\"name\": \"System\", \"color\": \"PINKISH\"}")) {
            mockMvc.perform(post("/api/admin/categories").with(userWith(Permission.CATEGORY_MANAGE))
                            .with(CsrfSupport.xsrf(mockMvc))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(categoryService);
    }

    @Test
    void deletesCategory() throws Exception {
        mockMvc.perform(delete("/api/admin/categories/1").with(userWith(Permission.CATEGORY_MANAGE))
                        .with(CsrfSupport.xsrf(mockMvc)))
                .andExpect(status().isNoContent());
        verify(categoryService).delete(eq("1"), any());
    }

    @Test
    void classificationListIsAvailableToBothPermissions() throws Exception {
        when(categoryService.list()).thenReturn(List.of(new CategoryInfo("1", "System", CategoryColor.GRAY)));

        mockMvc.perform(get("/api/container-categories").with(userWith(Permission.GAMESERVER_MANAGE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("System"));
        mockMvc.perform(get("/api/container-categories").with(userWith(Permission.CATEGORY_MANAGE)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/container-categories").with(userWith(Permission.DASHBOARD_VIEW)))
                .andExpect(status().isForbidden());
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
