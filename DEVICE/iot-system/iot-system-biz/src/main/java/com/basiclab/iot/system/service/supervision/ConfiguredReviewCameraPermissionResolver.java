package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewItemMapper;
import com.basiclab.iot.system.enums.permission.RoleCodeEnum;
import com.basiclab.iot.system.service.permission.PermissionService;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCameraPermissionRequest;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCameraPermissionResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@ConfigurationProperties(prefix = "yfeieye.review.camera-permission")
public class ConfiguredReviewCameraPermissionResolver implements ReviewCameraPermissionResolver {

    private static final Set<String> DIRECT_SUPER_ADMIN_ACTIONS = Set.of("playback", "snapshot", "alert_read");

    private Map<Long, List<String>> users = new LinkedHashMap<>();
    private Map<Long, List<String>> tenants = new LinkedHashMap<>();
    private Map<String, List<String>> actionPermissions = defaultActionPermissions();
    private List<String> defaultAllowedCameraIds = List.of();
    // Retained for configuration compatibility. Persisted fallback scope is always fail-closed.
    private boolean failClosed = true;
    private PermissionService permissionService;
    private SupervisionAlertReviewItemMapper reviewItemMapper;

    @Override
    public List<String> resolveAllowedCameraIds(ReviewCameraPermissionRequest request) {
        if (request == null || request.operatorUserId() == null || request.tenantId() == null) {
            return List.of();
        }
        if (!hasRequiredActionPermission(request)) {
            return List.of();
        }
        return resolveExplicitUserCameraIds(request);
    }

    private List<String> resolveExplicitUserCameraIds(ReviewCameraPermissionRequest request) {
        List<String> grantedCameraIds = normalizeValues(users.get(request.operatorUserId()));
        if (grantedCameraIds == null || grantedCameraIds.isEmpty()) {
            return List.of();
        }
        List<String> requestedCameraIds = normalizeValues(request.requestedCameraIds());
        List<String> candidateCameraIds;
        if (requestedCameraIds == null || requestedCameraIds.isEmpty()) {
            candidateCameraIds = grantedCameraIds;
        } else {
            Set<String> grantedCameraScope = new LinkedHashSet<>(grantedCameraIds);
            candidateCameraIds = requestedCameraIds.stream()
                    .filter(grantedCameraScope::contains)
                    .toList();
        }
        if (candidateCameraIds.isEmpty()) {
            return List.of();
        }
        if (canUseExplicitSuperAdminScope(request)) {
            return candidateCameraIds;
        }
        if (reviewItemMapper == null) {
            return List.of();
        }
        List<String> persistedCameraIds = normalizeValues(
                reviewItemMapper.selectExistingCameraIds(request.tenantId(), candidateCameraIds)
        );
        if (persistedCameraIds == null || persistedCameraIds.isEmpty()) {
            return List.of();
        }
        Set<String> persistedCameraScope = new LinkedHashSet<>(persistedCameraIds);
        return candidateCameraIds.stream()
                .filter(persistedCameraScope::contains)
                .toList();
    }

    private boolean canUseExplicitSuperAdminScope(ReviewCameraPermissionRequest request) {
        return DIRECT_SUPER_ADMIN_ACTIONS.contains(normalizeActionType(request.actionType()))
                && permissionService != null
                && permissionService.hasAnyRoles(
                        request.operatorUserId(), RoleCodeEnum.SUPER_ADMIN.getCode());
    }

    private boolean hasRequiredActionPermission(ReviewCameraPermissionRequest request) {
        List<String> permissions = actionPermissions.get(normalizeActionType(request.actionType()));
        List<String> normalizedPermissions = normalizeValues(permissions);
        if (normalizedPermissions == null || normalizedPermissions.isEmpty()) {
            return false;
        }
        if (permissionService == null || request.operatorUserId() == null) {
            return false;
        }
        return permissionService.hasAnyPermissions(request.operatorUserId(), normalizedPermissions.toArray(String[]::new));
    }

    private static String normalizeActionType(String actionType) {
        return actionType == null ? "" : actionType.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, List<String>> defaultActionPermissions() {
        Map<String, List<String>> defaults = new LinkedHashMap<>();
        defaults.put("playback", List.of("system:supervision-alert-review:media:playback"));
        defaults.put("snapshot", List.of("system:supervision-alert-review:media:snapshot"));
        defaults.put("coverage", List.of("system:supervision-alert-review:media:playback"));
        defaults.put("alert_read", List.of("system:supervision-alert-review:media:playback"));
        defaults.put("export", List.of("system:supervision-alert-review:media:export"));
        defaults.put("download", List.of("system:supervision-alert-review:media:download"));
        defaults.put("manifest_verify", List.of("system:supervision-alert-review:media:manifest"));
        defaults.put("record_manage", List.of("system:supervision-alert-review:media:manage"));
        return defaults;
    }

    private static List<String> normalizeValues(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream()
                .filter(value -> value != null && !value.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .toList();
    }

    public Map<Long, List<String>> getUsers() {
        return users;
    }

    public void setUsers(Map<Long, List<String>> users) {
        this.users = users == null ? new LinkedHashMap<>() : users;
    }

    public Map<Long, List<String>> getTenants() {
        return tenants;
    }

    public void setTenants(Map<Long, List<String>> tenants) {
        this.tenants = tenants == null ? new LinkedHashMap<>() : tenants;
    }

    public Map<String, List<String>> getActionPermissions() {
        return actionPermissions;
    }

    public void setActionPermissions(Map<String, List<String>> actionPermissions) {
        if (actionPermissions == null) {
            this.actionPermissions = new LinkedHashMap<>();
            return;
        }
        Map<String, List<String>> normalizedActionPermissions = new LinkedHashMap<>();
        actionPermissions.forEach((actionType, permissions) -> {
            String actionKey = normalizeActionType(actionType);
            if (!actionKey.isEmpty()) {
                normalizedActionPermissions.put(actionKey, permissions);
            }
        });
        this.actionPermissions = normalizedActionPermissions;
    }

    public List<String> getDefaultAllowedCameraIds() {
        return defaultAllowedCameraIds;
    }

    public void setDefaultAllowedCameraIds(List<String> defaultAllowedCameraIds) {
        this.defaultAllowedCameraIds = defaultAllowedCameraIds == null ? List.of() : defaultAllowedCameraIds;
    }

    public boolean isFailClosed() {
        return failClosed;
    }

    public void setFailClosed(boolean failClosed) {
        this.failClosed = failClosed;
    }

    @Autowired(required = false)
    public void setPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Autowired(required = false)
    public void setReviewItemMapper(SupervisionAlertReviewItemMapper reviewItemMapper) {
        this.reviewItemMapper = reviewItemMapper;
    }
}
