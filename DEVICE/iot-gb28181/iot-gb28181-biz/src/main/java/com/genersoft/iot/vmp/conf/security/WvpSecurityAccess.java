package com.genersoft.iot.vmp.conf.security;

import com.genersoft.iot.vmp.conf.security.dto.LoginUser;
import com.genersoft.iot.vmp.storager.dao.dto.Role;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * WVP 旧认证链的最小角色判断入口。
 */
@Component("wvpSecurity")
public class WvpSecurityAccess {

    private static final int ADMIN_ROLE_ID = 1;

    public boolean isAdmin(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof LoginUser)) {
            return false;
        }
        Role role = ((LoginUser) authentication.getPrincipal()).getRole();
        return role != null && role.getId() == ADMIN_ROLE_ID;
    }
}
