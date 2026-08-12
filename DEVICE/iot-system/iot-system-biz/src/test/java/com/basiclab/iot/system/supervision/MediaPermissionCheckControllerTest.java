package com.basiclab.iot.system.supervision;

import com.basiclab.iot.common.domain.CommonResult;
import com.basiclab.iot.common.domain.LoginUser;
import com.basiclab.iot.system.controller.admin.auth.AuthController;
import com.basiclab.iot.system.controller.admin.auth.vo.MediaPermissionCheckReqVO;
import com.basiclab.iot.system.controller.admin.auth.vo.MediaPermissionCheckRespVO;
import com.basiclab.iot.system.dal.dataobject.user.AdminUserDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewItemMapper;
import com.basiclab.iot.system.service.permission.PermissionService;
import com.basiclab.iot.system.service.supervision.ConfiguredReviewCameraPermissionResolver;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCameraPermissionRequest;
import com.basiclab.iot.system.service.user.AdminUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaPermissionCheckControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedTenantAndConfiguredCameraScopeAreAuthoritative() {
        AtomicReference<AdminUserDO> currentUser = new AtomicReference<>(user(7L));
        AdminUserService userService = userService(currentUser);
        ConfiguredReviewCameraPermissionResolver resolver = new ConfiguredReviewCameraPermissionResolver();
        resolver.setUsers(Map.of(42L, List.of("camera-01")));
        resolver.setActionPermissions(Map.of(
                "coverage", List.of("system:supervision-alert-review:media:playback")));
        resolver.setPermissionService(permissionService(true));
        resolver.setReviewItemMapper(reviewItemMapper(List.of("camera-01")));
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "reviewCameraPermissionResolver", resolver);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser().setId(42L).setTenantId(7L), null, List.of()));

        CommonResult<MediaPermissionCheckRespVO> granted = controller.checkMediaPermission(
                new MediaPermissionCheckReqVO("coverage", "camera-01", "/video/record/availability", null));
        CommonResult<MediaPermissionCheckRespVO> denied = controller.checkMediaPermission(
                new MediaPermissionCheckReqVO("coverage", "camera-02", "/video/record/availability", null));

        assertEquals(0, granted.getCode());
        assertTrue(granted.getData().getAllowed());
        assertEquals(42L, granted.getData().getUserId());
        assertEquals(7L, granted.getData().getTenantId());
        assertEquals("camera-01", granted.getData().getCameraId());
        assertEquals("coverage", granted.getData().getAction());
        assertEquals("granted", granted.getData().getReason());

        assertFalse(denied.getData().getAllowed());
        assertEquals(42L, denied.getData().getUserId());
        assertEquals(7L, denied.getData().getTenantId());
        assertEquals("camera_scope_denied", denied.getData().getReason());
    }

    @Test
    void missingTenantAndUnknownActionFailClosed() {
        AtomicReference<AdminUserDO> currentUser = new AtomicReference<>(user(null));
        AdminUserService userService = userService(currentUser);
        ConfiguredReviewCameraPermissionResolver resolver = new ConfiguredReviewCameraPermissionResolver();
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "reviewCameraPermissionResolver", resolver);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser().setId(42L), null, List.of()));

        MediaPermissionCheckRespVO missingTenant = controller.checkMediaPermission(
                new MediaPermissionCheckReqVO("snapshot", "camera-01", null, null)).getData();
        assertFalse(missingTenant.getAllowed());
        assertEquals("tenant_required", missingTenant.getReason());

        currentUser.set(user(7L));
        MediaPermissionCheckRespVO unknownAction = controller.checkMediaPermission(
                new MediaPermissionCheckReqVO("admin", "camera-01", null, null)).getData();
        assertFalse(unknownAction.getAllowed());
        assertEquals("action_permission_denied", unknownAction.getReason());

        resolver.setUsers(Map.of(42L, List.of("camera-01")));
        resolver.setActionPermissions(Map.of(
                "record_manage", List.of("system:supervision-alert-review:media:manage")));
        resolver.setPermissionService(permissionService(true));
        resolver.setReviewItemMapper(reviewItemMapper(List.of("camera-01")));
        ReflectionTestUtils.setField(controller, "permissionService", permissionService(true));
        MediaPermissionCheckRespVO missingManageCamera = controller.checkMediaPermission(
                new MediaPermissionCheckReqVO("record_manage", null, "/video/record/space/list", null)).getData();
        assertFalse(missingManageCamera.getAllowed());
        assertEquals("camera_scope_required", missingManageCamera.getReason());

        MediaPermissionCheckRespVO grantedManage = controller.checkMediaPermission(
                new MediaPermissionCheckReqVO("record_manage", "camera-01", "/video/record/space/list", null)).getData();
        assertTrue(grantedManage.getAllowed());
        assertEquals("camera-01", grantedManage.getCameraId());
        assertEquals("granted", grantedManage.getReason());

        MediaPermissionCheckRespVO deniedManage = controller.checkMediaPermission(
                new MediaPermissionCheckReqVO("record_manage", "camera-02", "/video/record/space/list", null)).getData();
        assertFalse(deniedManage.getAllowed());
        assertEquals("camera_scope_denied", deniedManage.getReason());
    }

    @Test
    void alertCollectionRequiresOneExplicitSuperAdminCameraScope() {
        AtomicReference<AdminUserDO> currentUser = new AtomicReference<>(user(7L));
        ConfiguredReviewCameraPermissionResolver resolver = new ConfiguredReviewCameraPermissionResolver();
        resolver.setUsers(Map.of(42L, List.of("camera-01")));
        resolver.setActionPermissions(Map.of(
                "alert_read", List.of("system:supervision-alert-review:media:playback")));
        resolver.setPermissionService(permissionService(true));
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "userService", userService(currentUser));
        ReflectionTestUtils.setField(controller, "reviewCameraPermissionResolver", resolver);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser().setId(42L).setTenantId(7L), null, List.of()));

        MediaPermissionCheckRespVO granted = controller.checkMediaPermission(
                new MediaPermissionCheckReqVO("alert_read", null, "/video/alert/statistics", null)).getData();

        assertTrue(granted.getAllowed());
        assertEquals("camera-01", granted.getCameraId());
        assertEquals("granted", granted.getReason());

        resolver.setUsers(Map.of(42L, List.of("camera-01", "camera-02")));
        MediaPermissionCheckRespVO ambiguous = controller.checkMediaPermission(
                new MediaPermissionCheckReqVO("alert_read", null, "/video/alert/page", null)).getData();

        assertFalse(ambiguous.getAllowed());
        assertEquals("camera_scope_ambiguous", ambiguous.getReason());
    }

    @Test
    void defaultAlertReadPermissionNeverExpandsTheExplicitCameraGrant() {
        AtomicReference<List<String>> requestedPermissions = new AtomicReference<>();
        ConfiguredReviewCameraPermissionResolver resolver = new ConfiguredReviewCameraPermissionResolver();
        resolver.setUsers(Map.of(42L, List.of("camera-01")));
        resolver.setPermissionService(permissionService(true, requestedPermissions));
        resolver.setReviewItemMapper(reviewItemMapper(List.of("camera-01")));

        List<String> granted = resolver.resolveAllowedCameraIds(
                new ReviewCameraPermissionRequest(null, 42L, 7L, "alert_read", List.of("camera-01")));
        List<String> denied = resolver.resolveAllowedCameraIds(
                new ReviewCameraPermissionRequest(null, 42L, 7L, "alert_read", List.of("camera-02")));

        assertEquals(List.of("camera-01"), granted);
        assertTrue(denied.isEmpty());
        assertEquals(List.of("system:supervision-alert-review:media:playback"), requestedPermissions.get());
    }

    private static AdminUserDO user(Long tenantId) {
        AdminUserDO user = AdminUserDO.builder().id(42L).build();
        user.setTenantId(tenantId);
        return user;
    }

    private static AdminUserService userService(AtomicReference<AdminUserDO> currentUser) {
        return (AdminUserService) Proxy.newProxyInstance(
                AdminUserService.class.getClassLoader(),
                new Class<?>[]{AdminUserService.class},
                (proxy, method, args) -> {
                    if ("getUser".equals(method.getName()) && args != null && args.length == 1) {
                        return currentUser.get();
                    }
                    if ("toString".equals(method.getName())) {
                        return "MediaPermissionCheckControllerTest.AdminUserService";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static PermissionService permissionService(boolean allowed) {
        return permissionService(allowed, null);
    }

    private static PermissionService permissionService(
            boolean allowed,
            AtomicReference<List<String>> requestedPermissions) {
        return (PermissionService) Proxy.newProxyInstance(
                PermissionService.class.getClassLoader(),
                new Class<?>[]{PermissionService.class},
                (proxy, method, args) -> {
                    if ("hasAnyPermissions".equals(method.getName())) {
                        if (requestedPermissions != null) {
                            requestedPermissions.set(List.of((String[]) args[1]));
                        }
                        return allowed;
                    }
                    if ("hasAnyRoles".equals(method.getName())) {
                        return allowed;
                    }
                    if ("toString".equals(method.getName())) {
                        return "MediaPermissionCheckControllerTest.PermissionService";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static SupervisionAlertReviewItemMapper reviewItemMapper(List<String> persistedCameraIds) {
        return (SupervisionAlertReviewItemMapper) Proxy.newProxyInstance(
                SupervisionAlertReviewItemMapper.class.getClassLoader(),
                new Class<?>[]{SupervisionAlertReviewItemMapper.class},
                (proxy, method, args) -> {
                    if ("selectExistingCameraIds".equals(method.getName())) {
                        return persistedCameraIds;
                    }
                    if ("toString".equals(method.getName())) {
                        return "MediaPermissionCheckControllerTest.ReviewItemMapper";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
