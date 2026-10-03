package com.github.kevinldg.backend.container;

import com.github.kevinldg.backend.auth.AuthenticatedUser;
import com.github.kevinldg.backend.common.ApiException;
import com.github.kevinldg.backend.maintenance.MaintenanceSettingsRepository;
import com.github.kevinldg.backend.role.Permission;
import com.github.kevinldg.backend.role.Role;
import com.github.kevinldg.backend.role.RoleRepository;
import com.github.kevinldg.backend.setup.DataInitializer;
import com.github.kevinldg.backend.support.CsrfSupport;
import com.github.kevinldg.backend.user.User;
import com.github.kevinldg.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.net.ConnectException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.mongodb.uri=mongodb://localhost:27017/serverdashboard-test")
// The maintenance state would otherwise be read from MongoDB; unknown means "off".
@MockitoBean(types = MaintenanceSettingsRepository.class)
@AutoConfigureMockMvc
class ContainerControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ContainerService containerService;

    @MockitoBean
    ContainerLogStreamService logStreamService;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    RoleRepository roleRepository;

    @MockitoBean
    DataInitializer dataInitializer;

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/containers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void overviewRequiresDashboardPermission() throws Exception {
        mockMvc.perform(get("/api/containers").with(userWith(Permission.CONTAINER_VIEW)))
                .andExpect(status().isForbidden());

        when(containerService.getOverview()).thenReturn(new ContainerOverviewResponse(
                new ContainerOverviewResponse.Statistics(0, 0, 0), List.of()));
        mockMvc.perform(get("/api/containers").with(userWith(Permission.DASHBOARD_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistics.total").value(0));
    }

    @Test
    void detailsRequireContainerPermission() throws Exception {
        mockMvc.perform(get("/api/containers/abc").with(userWith(Permission.DASHBOARD_VIEW)))
                .andExpect(status().isForbidden());
        verify(containerService, never()).getDetails(anyString(), anyBoolean());
    }

    @Test
    void detailsWithholdEnvironmentWithoutPermission() throws Exception {
        mockMvc.perform(get("/api/containers/abc").with(userWith(Permission.CONTAINER_VIEW)))
                .andExpect(status().isOk());
        verify(containerService).getDetails("abc", false);
    }

    @Test
    void detailsIncludeEnvironmentWithPermission() throws Exception {
        mockMvc.perform(get("/api/containers/abc").with(userWith(Permission.CONTAINER_VIEW, Permission.CONTAINER_ENV_VIEW)))
                .andExpect(status().isOk());
        verify(containerService).getDetails("abc", true);
    }

    @Test
    void adminsSeeEverything() throws Exception {
        mockMvc.perform(get("/api/containers/abc").with(admin()))
                .andExpect(status().isOk());
        verify(containerService).getDetails("abc", true);
    }

    @Test
    void invalidContainerIdIsRejected() throws Exception {
        mockMvc.perform(get("/api/containers/-abc").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        verify(containerService, never()).getDetails(anyString(), anyBoolean());
    }

    @Test
    void logsRequireLogsPermission() throws Exception {
        mockMvc.perform(get("/api/containers/abc/logs").with(userWith(Permission.CONTAINER_VIEW)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/containers/abc/logs").with(userWith(Permission.CONTAINER_LOGS_VIEW)))
                .andExpect(status().isOk());
        verify(containerService).getLogs("abc", 500);
    }

    @Test
    void liveLogsRequireLogsPermission() throws Exception {
        mockMvc.perform(get("/api/containers/abc/logs/stream").with(userWith(Permission.CONTAINER_VIEW)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(logStreamService);

        when(logStreamService.openStream(eq("abc"), any())).thenReturn(new SseEmitter());
        mockMvc.perform(get("/api/containers/abc/logs/stream").with(userWith(Permission.CONTAINER_LOGS_VIEW)))
                .andExpect(request().asyncStarted());
    }

    @Test
    void logsTailIsLimited() throws Exception {
        mockMvc.perform(get("/api/containers/abc/logs").param("tail", "0").with(admin()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/containers/abc/logs").param("tail", "5001").with(admin()))
                .andExpect(status().isBadRequest());
        verify(containerService, never()).getLogs(anyString(), anyInt());
    }

    @Test
    void dockerErrorsShowTechnicalDetailsToAdminsOnly() throws Exception {
        when(containerService.getOverview()).thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "The Docker host is currently unavailable.", new ConnectException("Connection refused")));

        mockMvc.perform(get("/api/containers").with(userWith(Permission.DASHBOARD_VIEW)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("The Docker host is currently unavailable."))
                .andExpect(jsonPath("$.exception").doesNotExist());

        mockMvc.perform(get("/api/containers").with(admin()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.exception").value(ConnectException.class.getName()))
                .andExpect(jsonPath("$.exceptionMessage").value("Connection refused"));
    }

    static Stream<Arguments> actions() {
        return Stream.of(
                action("start", HttpMethod.POST, "/api/containers/abc/start", Permission.CONTAINER_START,
                        service -> {
                            service.start("abc", "tester");
                            return null;
                        }),
                action("stop", HttpMethod.POST, "/api/containers/abc/stop", Permission.CONTAINER_STOP,
                        service -> {
                            service.stop("abc", "tester");
                            return null;
                        }),
                action("restart", HttpMethod.POST, "/api/containers/abc/restart", Permission.CONTAINER_RESTART,
                        service -> {
                            service.restart("abc", "tester");
                            return null;
                        }),
                action("force stop", HttpMethod.POST, "/api/containers/abc/force-stop", Permission.CONTAINER_FORCE_STOP,
                        service -> {
                            service.forceStop("abc", "tester");
                            return null;
                        }),
                action("delete", HttpMethod.DELETE, "/api/containers/abc", Permission.CONTAINER_DELETE,
                        service -> {
                            service.delete("abc", "tester");
                            return null;
                        }));
    }

    private static Arguments action(String name, HttpMethod method, String path, Permission permission,
                                    Function<ContainerService, Void> expectedCall) {
        return Arguments.of(name, method, path, permission, expectedCall);
    }

    @ParameterizedTest(name = "{0} requires {3}")
    @MethodSource("actions")
    void actionsRequireTheirPermission(String name, HttpMethod method, String path, Permission permission,
                                       Function<ContainerService, Void> expectedCall) throws Exception {
        // Every other permission is not enough
        Permission[] allOthers = EnumSet.complementOf(EnumSet.of(permission)).toArray(Permission[]::new);
        mockMvc.perform(request(method, path).with(userWith(allOthers)).with(CsrfSupport.xsrf(mockMvc)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(containerService);

        mockMvc.perform(request(method, path).with(userWith(permission)).with(CsrfSupport.xsrf(mockMvc)))
                .andExpect(status().isNoContent());
        expectedCall.apply(verify(containerService));
    }

    @ParameterizedTest(name = "{0} requires a CSRF token")
    @MethodSource("actions")
    void actionsRequireCsrfToken(String name, HttpMethod method, String path) throws Exception {
        mockMvc.perform(request(method, path).with(admin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", containsString("CSRF")));
        verifyNoInteractions(containerService);
    }

    @Test
    void deletingRunningContainerReturnsConflict() throws Exception {
        doThrow(new ApiException(HttpStatus.CONFLICT, "Stop the container before deleting it."))
                .when(containerService).delete("abc", "tester");

        mockMvc.perform(delete("/api/containers/abc").with(admin()).with(CsrfSupport.xsrf(mockMvc)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Stop the container before deleting it."));
    }

    private RequestPostProcessor userWith(Permission... permissions) {
        Role role = new Role();
        role.setId("role-test");
        role.setName("Test");
        role.setPermissions(permissions.length == 0 ? EnumSet.noneOf(Permission.class) : EnumSet.of(permissions[0], permissions));
        return loggedInAs(role);
    }

    private RequestPostProcessor admin() {
        Role role = new Role();
        role.setId("role-admin");
        role.setName("Admin");
        role.setSuperuser(true);
        return loggedInAs(role);
    }

    /**
     * Simulates a logged-in session. The user is reloaded from the (mocked) database on each request,
     * so the role's permissions are what counts.
     */
    private RequestPostProcessor loggedInAs(Role role) {
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
