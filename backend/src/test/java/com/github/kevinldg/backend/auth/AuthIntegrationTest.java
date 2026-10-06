package com.github.kevinldg.backend.auth;

import com.github.kevinldg.backend.audit.AuditAction;
import com.github.kevinldg.backend.audit.AuditLogRepository;
import com.github.kevinldg.backend.audit.AuditOutcome;
import com.github.kevinldg.backend.maintenance.MaintenanceSettingsRepository;
import com.github.kevinldg.backend.role.Permission;
import com.github.kevinldg.backend.role.Role;
import com.github.kevinldg.backend.role.RoleRepository;
import com.github.kevinldg.backend.setup.DataInitializer;
import com.github.kevinldg.backend.support.CsrfSupport;
import com.github.kevinldg.backend.user.User;
import com.github.kevinldg.backend.user.UserRepository;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.mongodb.uri=mongodb://localhost:27017/serverdashboard-test")
// The maintenance state would otherwise be read from MongoDB; unknown means "off".
@MockitoBean(types = {MaintenanceSettingsRepository.class, AuditLogRepository.class})
@AutoConfigureMockMvc
class AuthIntegrationTest {

    private static final String PASSWORD = "correct-password-123";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    AuditLogRepository auditLogRepository;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    RoleRepository roleRepository;

    @MockitoBean
    DataInitializer dataInitializer;

    private User user;

    @BeforeEach
    void setUp() {
        Role userRole = new Role();
        userRole.setId("role-user");
        userRole.setName("User");
        userRole.setPermissions(EnumSet.of(Permission.DASHBOARD_VIEW, Permission.CONTAINER_VIEW));

        Role adminRole = new Role();
        adminRole.setId("role-admin");
        adminRole.setName("Admin");
        adminRole.setSuperuser(true);

        user = new User();
        user.setId("user-1");
        user.setUsername("alice");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRoleId(userRole.getId());
        user.setActive(true);
        user.setPasswordChangeRecommended(true);

        when(userRepository.findByUsername("alice")).thenAnswer(invocation -> Optional.of(user));
        when(userRepository.findById("user-1")).thenAnswer(invocation -> Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleRepository.findById("role-user")).thenReturn(Optional.of(userRole));
        when(roleRepository.findById("role-admin")).thenReturn(Optional.of(adminRole));
    }

    @Test
    void csrfEndpointIssuesTokenCookie() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void spaCsrfFlowAllowsLoginAndRotatesToken() throws Exception {
        Cookie csrfCookie = mockMvc.perform(get("/api/auth/csrf"))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();

        // What axios does: send the cookie back and copy its value into the X-XSRF-TOKEN header.
        Cookie[] responseCookies = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .param("username", "alice")
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookies();

        // The response first deletes the old token cookie, then sets the new one; browsers keep the last one.
        Cookie rotatedCookie = Arrays.stream(responseCookies)
                .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(rotatedCookie.getValue()).isNotBlank().isNotEqualTo(csrfCookie.getValue());
    }

    @Test
    void loginReturnsCurrentUserAndRecordsLogin() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(xsrf())
                        .param("username", " Alice ")
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-1"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role.name").value("User"))
                .andExpect(jsonPath("$.admin").value(false))
                .andExpect(jsonPath("$.permissions", hasItem("DASHBOARD_VIEW")))
                .andExpect(jsonPath("$.passwordChangeRecommended").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(userRepository).save(argThat(saved -> saved.getLastLoginAt() != null));
        verify(auditLogRepository).save(argThat(entry -> entry.getAction() == AuditAction.LOGIN
                && entry.getActor().equals("alice")
                && "127.0.0.1".equals(entry.getIp())));
    }

    @Test
    void loginWithWrongPasswordFails() throws Exception {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        mockMvc.perform(post("/api/auth/login").with(xsrf())
                        .param("username", "alice")
                        .param("password", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));

        verifyFailedLoginRecorded("alice", "Login failed: wrong password");
    }

    @Test
    void loginWithUnknownUserFailsWithSameMessage() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(xsrf())
                        .param("username", "bob")
                        .param("password", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));

        verifyFailedLoginRecorded("bob", "Login failed: unknown user");
    }

    @Test
    void loginWithDeactivatedUserFailsWithSameMessage() throws Exception {
        user.setActive(false);

        mockMvc.perform(post("/api/auth/login").with(xsrf())
                        .param("username", "alice")
                        .param("password", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));

        verifyFailedLoginRecorded("alice", "Login failed: account deactivated");
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .param("username", "alice")
                        .param("password", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("CSRF")));
    }

    @Test
    void currentUserRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Authentication required."));
    }

    @Test
    void currentUserIsReturnedForSession() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void deactivatedUserLosesSessionImmediately() throws Exception {
        MockHttpSession session = login();
        user.setActive(false);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void sessionEndsAfterAdminPasswordReset() throws Exception {
        MockHttpSession session = login();
        // What UserManagementService.resetPassword does
        user.setSessionVersion(user.getSessionVersion() + 1);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void roleChangeAppliesToNextRequest() throws Exception {
        MockHttpSession session = login();
        user.setRoleId("role-admin");

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value(true))
                .andExpect(jsonPath("$.permissions", hasSize(Permission.values().length)));
    }

    @Test
    void logoutEndsSession() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/api/auth/logout").with(xsrf()).session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
        verify(auditLogRepository).save(argThat(entry -> entry.getAction() == AuditAction.LOGOUT
                && entry.getActor().equals("alice")
                && "127.0.0.1".equals(entry.getIp())));
    }

    @Test
    void changePasswordUpdatesHashAndClearsRecommendation() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(put("/api/auth/password").with(xsrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s", "newPassword": "a-new-secure-password"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isNoContent());

        assertThat(passwordEncoder.matches("a-new-secure-password", user.getPasswordHash())).isTrue();
        assertThat(user.isPasswordChangeRecommended()).isFalse();
        verify(userRepository, atLeastOnce()).save(user);
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(put("/api/auth/password").with(xsrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "wrong-password", "newPassword": "a-new-secure-password"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The current password is incorrect."));
        // Only login events store the client address
        verify(auditLogRepository).save(argThat(entry -> entry.getAction() == AuditAction.PASSWORD_CHANGE
                && entry.getOutcome() == AuditOutcome.FAILURE
                && entry.getIp() == null));
    }

    private void verifyFailedLoginRecorded(String actor, String summary) {
        verify(auditLogRepository).save(argThat(entry -> entry.getAction() == AuditAction.LOGIN_FAILED
                && entry.getOutcome() == AuditOutcome.FAILURE
                && entry.getActor().equals(actor)
                && entry.getSummary().equals(summary)
                && "127.0.0.1".equals(entry.getIp())));
    }

    @Test
    void changePasswordValidatesInput() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(put("/api/auth/password").with(xsrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s", "newPassword": "short"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.newPassword").exists());
    }

    private RequestPostProcessor xsrf() throws Exception {
        return CsrfSupport.xsrf(mockMvc);
    }

    private MockHttpSession login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(xsrf())
                        .param("username", "alice")
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
