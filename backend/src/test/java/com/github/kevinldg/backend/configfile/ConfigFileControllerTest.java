package com.github.kevinldg.backend.configfile;

import com.github.kevinldg.backend.audit.AuditLogRepository;
import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.configfile.ConfigFileResponses.Overview;
import com.github.kevinldg.backend.configfile.ConfigFileResponses.SaveResponse;
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

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.mongodb.uri=mongodb://localhost:27017/serverdashboard-test")
@MockitoBean(types = {MaintenanceSettingsRepository.class, AuditLogRepository.class})
@AutoConfigureMockMvc
class ConfigFileControllerTest {

    private static final String SAVE = """
            {"path": "/data/server.properties", "content": "motd=x", "expectedSha256": "abc"}
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ConfigFileService configFileService;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    RoleRepository roleRepository;

    @MockitoBean
    DataInitializer dataInitializer;

    @Test
    void readingRequiresViewPermission() throws Exception {
        mockMvc.perform(get("/api/containers/mc/config-files").with(userWith(Permission.CONTAINER_VIEW)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/containers/mc/config-files/content").param("path", "/data/server.properties")
                        .with(userWith(Permission.CONTAINER_VIEW)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(configFileService);

        when(configFileService.getOverview("mc")).thenReturn(new Overview(List.of("/data"), List.of(), true));
        mockMvc.perform(get("/api/containers/mc/config-files").with(userWith(Permission.GAMESERVER_CONFIG_VIEW)))
                .andExpect(status().isOk());
    }

    @Test
    void savingRequiresEditPermission() throws Exception {
        mockMvc.perform(put("/api/containers/mc/config-files/content").with(userWith(Permission.GAMESERVER_CONFIG_VIEW))
                        .with(CsrfSupport.xsrf(mockMvc)).contentType(MediaType.APPLICATION_JSON).content(SAVE))
                .andExpect(status().isForbidden());
        verifyNoInteractions(configFileService);

        when(configFileService.saveFile(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new SaveResponse("/data/server.properties", "def", Instant.now()));
        mockMvc.perform(put("/api/containers/mc/config-files/content").with(userWith(Permission.GAMESERVER_CONFIG_EDIT))
                        .with(CsrfSupport.xsrf(mockMvc)).contentType(MediaType.APPLICATION_JSON).content(SAVE))
                .andExpect(status().isOk());
        verify(configFileService).saveFile(eq("mc"), eq("/data/server.properties"), eq("motd=x"), eq("abc"), any());
    }

    @Test
    void savingRequiresCsrfTokenAndExpectedHash() throws Exception {
        mockMvc.perform(put("/api/containers/mc/config-files/content").with(userWith(Permission.GAMESERVER_CONFIG_EDIT))
                        .contentType(MediaType.APPLICATION_JSON).content(SAVE))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/containers/mc/config-files/content").with(userWith(Permission.GAMESERVER_CONFIG_EDIT))
                        .with(CsrfSupport.xsrf(mockMvc)).contentType(MediaType.APPLICATION_JSON)
                        .content(SAVE.replace("\"abc\"", "\"\"")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(configFileService);
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
