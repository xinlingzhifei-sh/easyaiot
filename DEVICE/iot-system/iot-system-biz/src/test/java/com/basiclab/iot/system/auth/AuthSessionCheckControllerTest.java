package com.basiclab.iot.system.auth;

import com.basiclab.iot.common.domain.LoginUser;
import com.basiclab.iot.system.controller.admin.auth.AuthController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthSessionCheckControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedSessionReturnsNoContent() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new LoginUser().setId(42L).setTenantId(7L),
                        null,
                        List.of()
                )
        );

        ResponseEntity<Void> response = new AuthController().checkSession();

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void unauthenticatedSessionReturnsHttpUnauthorized() {
        ResponseEntity<Void> response = new AuthController().checkSession();

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void unauthenticatedSessionWritesUnauthorizedThroughMvc() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthController()).build();

        mockMvc.perform(get("/system/auth/check-session"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

}
