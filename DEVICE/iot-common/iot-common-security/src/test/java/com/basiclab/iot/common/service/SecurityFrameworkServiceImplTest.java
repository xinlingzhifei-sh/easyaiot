package com.basiclab.iot.common.service;

import com.basiclab.iot.common.domain.CommonResult;
import com.basiclab.iot.common.domain.LoginUser;
import com.basiclab.iot.common.enums.UserTypeEnum;
import com.basiclab.iot.system.api.permission.PermissionApi;
import com.basiclab.iot.system.api.permission.dto.DeptDataPermissionRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityFrameworkServiceImplTest {

    private final RecordingPermissionApi permissionApi = new RecordingPermissionApi();
    private final SecurityFrameworkServiceImpl service = new SecurityFrameworkServiceImpl(permissionApi);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void appUserCannotReuseAdminRoleThroughCollidingUserId() {
        authenticate(1L, UserTypeEnum.MEMBER.getValue());

        assertFalse(service.isAdminUser());
        assertFalse(service.hasRole("super_admin"));
        assertEquals(0, permissionApi.roleChecks);
    }

    @Test
    void adminUserRoleStillUsesPermissionService() {
        authenticate(1L, UserTypeEnum.ADMIN.getValue());

        assertTrue(service.isAdminUser());
        assertTrue(service.hasRole("super_admin"));
        assertEquals(1, permissionApi.roleChecks);
    }

    private static void authenticate(Long userId, Integer userType) {
        LoginUser loginUser = new LoginUser().setId(userId).setUserType(userType);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        loginUser, null, Collections.emptyList()));
    }

    private static final class RecordingPermissionApi implements PermissionApi {

        private int roleChecks;

        @Override
        public CommonResult<Set<Long>> getUserRoleIdListByRoleIds(Collection<Long> roleIds) {
            return CommonResult.success(Collections.emptySet());
        }

        @Override
        public CommonResult<Boolean> hasAnyPermissions(Long userId, String... permissions) {
            return CommonResult.success(true);
        }

        @Override
        public CommonResult<Boolean> hasAnyRoles(Long userId, String... roles) {
            roleChecks++;
            return CommonResult.success(true);
        }

        @Override
        public CommonResult<DeptDataPermissionRespDTO> getDeptDataPermission(Long userId) {
            return CommonResult.success(null);
        }
    }
}
