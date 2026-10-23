package com.basiclab.iot.system.framework.supervision;

import com.basiclab.iot.system.service.permission.PermissionService;
import com.basiclab.iot.system.service.supervision.ReviewOperatorPermissionChecker;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 同进程部署时，把 supervision 的权限端口原样委托给平台 PermissionService。
 */
@Component
public class PermissionServiceReviewOperatorPermissionChecker implements ReviewOperatorPermissionChecker {

    private final PermissionService permissionService;

    public PermissionServiceReviewOperatorPermissionChecker(PermissionService permissionService) {
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService");
    }

    @Override
    public boolean hasAnyPermissions(Long userId, String... permissions) {
        return permissionService.hasAnyPermissions(userId, permissions);
    }

    @Override
    public boolean hasAnyRoles(Long userId, String... roles) {
        return permissionService.hasAnyRoles(userId, roles);
    }
}
