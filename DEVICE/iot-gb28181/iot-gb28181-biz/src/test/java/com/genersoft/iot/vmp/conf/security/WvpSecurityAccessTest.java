package com.genersoft.iot.vmp.conf.security;

import com.genersoft.iot.vmp.conf.security.dto.LoginUser;
import com.genersoft.iot.vmp.storager.dao.dto.Role;
import com.genersoft.iot.vmp.storager.dao.dto.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WvpSecurityAccessTest {

    private final WvpSecurityAccess access = new WvpSecurityAccess();

    @Test
    void allowsBuiltInAdminRole() {
        assertTrue(access.isAdmin(authenticationForRole(1)));
    }

    @Test
    void rejectsNonAdminRole() {
        assertFalse(access.isAdmin(authenticationForRole(2)));
    }

    @Test
    void rejectsMissingOrUnexpectedPrincipal() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("anonymousUser");

        assertFalse(access.isAdmin(null));
        assertFalse(access.isAdmin(authentication));
    }

    private static Authentication authenticationForRole(int roleId) {
        Role role = new Role();
        role.setId(roleId);
        User user = new User();
        user.setRole(role);
        LoginUser loginUser = new LoginUser(user, LocalDateTime.now());
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(loginUser);
        return authentication;
    }
}
