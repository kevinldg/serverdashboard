package com.github.kevinldg.backend.announcement;

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
class AnnouncementControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AnnouncementService announcementService;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    RoleRepository roleRepository;

    @MockitoBean
    DataInitializer dataInitializer;

    @Test
    void visibleAnnouncementsRequireDashboardPermission() throws Exception {
        mockMvc.perform(get("/api/announcements").with(userWith(Permission.CONTAINER_VIEW)))
                .andExpect(status().isForbidden());

        when(announcementService.listVisible()).thenReturn(List.of(new VisibleAnnouncement("a1", "Title", "Text", null, null)));
        mockMvc.perform(get("/api/announcements").with(userWith(Permission.DASHBOARD_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Title"));
    }

    @Test
    void managementRequiresAnnouncementPermission() throws Exception {
        Permission[] allButManage = EnumSet.complementOf(EnumSet.of(Permission.ANNOUNCEMENT_MANAGE)).toArray(Permission[]::new);

        mockMvc.perform(get("/api/admin/announcements").with(userWith(allButManage)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/announcements").with(userWith(allButManage)).with(CsrfSupport.xsrf(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "T", "message": "M", "active": true}
                                """))
                .andExpect(status().isForbidden());
        verifyNoInteractions(announcementService);
    }

    @Test
    void invalidAnnouncementIsRejected() throws Exception {
        mockMvc.perform(post("/api/admin/announcements").with(userWith(Permission.ANNOUNCEMENT_MANAGE))
                        .with(CsrfSupport.xsrf(mockMvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "", "message": "M"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.active").exists());
        verifyNoInteractions(announcementService);
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
