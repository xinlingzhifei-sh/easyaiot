package com.basiclab.iot.system.service.supervision;

/**
 * 复核媒体权限判断所需的平台权限端口。
 *
 * <p>supervision 模块不直接依赖 iot-system 的 PermissionService；同进程部署时由 iot-system-biz
 * 提供委托 PermissionService 的实现，独立部署时可改为基于 PermissionApi 的远程实现。</p>
 */
public interface ReviewOperatorPermissionChecker {

    boolean hasAnyPermissions(Long userId, String... permissions);

    boolean hasAnyRoles(Long userId, String... roles);
}
