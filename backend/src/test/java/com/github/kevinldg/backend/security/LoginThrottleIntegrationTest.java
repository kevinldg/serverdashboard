package com.github.kevinldg.backend.security;

import com.github.kevinldg.backend.maintenance.MaintenanceSettingsRepository;
import com.github.kevinldg.backend.role.Role;
import com.github.kevinldg.backend.role.RoleRepository;
import com.github.kevinldg.backend.setup.DataInitializer;
import com.github.kevinldg.backend.support.CsrfSupport;
import com.github.kevinldg.backend.user.User;
import com.github.kevinldg.backend.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.mongodb.uri=mongodb://localhost:27017/serverdashboard-test")
@MockitoBean(types = MaintenanceSettingsRepository.class)
@AutoConfigureMockMvc
class LoginThrottleIntegrationTest {

    private static final String PASSWORD = "correct-password-123";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    LoginThrottle loginThrottle;

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    RoleRepository roleRepository;

    @MockitoBean
    DataInitializer dataInitializer;

    @BeforeEach
    void setUp() {
        loginThrottle.clear();
        Role role = new Role();
        role.setId("role-user");
        role.setName("User");
        User user = new User();
        user.setId("user-1");
        user.setUsername("alice");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRoleId("role-user");
        user.setActive(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleRepository.findById("role-user")).thenReturn(Optional.of(role));
    }

    @AfterEach
    void tearDown() {
        loginThrottle.clear();
    }

    @Test
    void sixthAttemptIsBlockedEvenWithTheCorrectPassword() throws Exception {
        for (int i = 0; i < LoginThrottle.MAX_FAILURES_PER_USERNAME; i++) {
            login("wrong-password").andExpect(status().isUnauthorized());
        }

        login(PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.detail", containsString("Too many failed login attempts")));
    }

    @Test
    void successfulLoginResetsTheCounter() throws Exception {
        for (int i = 0; i < LoginThrottle.MAX_FAILURES_PER_USERNAME - 1; i++) {
            login("wrong-password").andExpect(status().isUnauthorized());
        }
        login(PASSWORD).andExpect(status().isOk());
        login("wrong-password").andExpect(status().isUnauthorized());
        login(PASSWORD).andExpect(status().isOk());
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(CsrfSupport.xsrf(mockMvc))
                .param("username", "alice")
                .param("password", password));
    }
}
