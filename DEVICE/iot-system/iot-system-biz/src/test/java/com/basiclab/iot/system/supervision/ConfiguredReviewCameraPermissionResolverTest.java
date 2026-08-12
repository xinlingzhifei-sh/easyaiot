package com.basiclab.iot.system.supervision;

import com.basiclab.iot.system.api.permission.dto.DeptDataPermissionRespDTO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewItemMapper;
import com.basiclab.iot.system.service.permission.PermissionService;
import com.basiclab.iot.system.service.supervision.ConfiguredReviewCameraPermissionResolver;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCameraPermissionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfiguredReviewCameraPermissionResolverTest {

    @Test
    void applicationYamlBindsExplicitAdminCameraGrantThroughSpringBinder() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(CameraPermissionBindingConfiguration.class)
                .withPropertyValues("YFEIEYE_REVIEW_CAMERA_PERMISSION_USERS_1=camera-01,camera-02")
                .run(context -> {
                    ConfiguredReviewCameraPermissionResolver resolver =
                            context.getBean(ConfiguredReviewCameraPermissionResolver.class);
                    assertEquals(List.of("camera-01", "camera-02"), resolver.getUsers().get(1L));
                    assertEquals(
                            List.of("system:supervision-alert-review:media:manage"),
                            resolver.getActionPermissions().get("record_manage")
                    );
                    assertEquals(
                            List.of("system:supervision-alert-review:media:playback"),
                            resolver.getActionPermissions().get("alert_read")
                    );
                });
    }

    @Test
    void alertReadUsesExplicitSuperAdminCameraGrantWithoutQueryingReviewHistory() {
        ConfiguredReviewCameraPermissionResolver resolver = new ConfiguredReviewCameraPermissionResolver();
        resolver.setUsers(Map.of(7L, List.of("camera-01")));
        resolver.setReviewItemMapper(reviewItemMapperThatMustNotBeCalled());
        CapturingPermissionService permissionService = new CapturingPermissionService();
        permissionService.allowed = true;
        permissionService.superAdmin = true;
        resolver.setPermissionService(permissionService);

        assertEquals(
                List.of("camera-01"),
                resolver.resolveAllowedCameraIds(
                        new ReviewCameraPermissionRequest(
                                1L,
                                7L,
                                10L,
                                "alert_read",
                                List.of("camera-01")
                        )
                )
        );
    }

    @Test
    void sameTenantReviewHistoryDoesNotAuthorizeUserWithoutExplicitCameraGrant() {
        ConfiguredReviewCameraPermissionResolver resolver = new ConfiguredReviewCameraPermissionResolver();
        resolver.setReviewItemMapper(reviewItemMapper(Map.of(
                10L, List.of("camera-01")
        )));
        allowAction(resolver, "playback");

        assertEquals(
                List.of(),
                resolver.resolveAllowedCameraIds(
                        new ReviewCameraPermissionRequest(
                                1L,
                                7L,
                                10L,
                                "playback",
                                List.of("camera-01")
                        )
                )
        );
    }

    @Test
    void explicitUserGrantIsIntersectedWithRequestedAndTenantOwnedCameras() {
        ConfiguredReviewCameraPermissionResolver resolver = new ConfiguredReviewCameraPermissionResolver();
        resolver.setUsers(Map.of(7L, List.of("camera-01", "camera-02", "camera-03")));
        resolver.setReviewItemMapper(reviewItemMapper(Map.of(
                10L, List.of("camera-01", "camera-02"),
                20L, List.of("camera-03")
        )));
        resolver.setActionPermissions(Map.of(
                "download", List.of("system:supervision-alert-review:media:download")
        ));
        CapturingPermissionService permissionService = new CapturingPermissionService();
        permissionService.allowed = true;
        resolver.setPermissionService(permissionService);

        assertEquals(
                List.of("camera-02"),
                resolver.resolveAllowedCameraIds(
                        new ReviewCameraPermissionRequest(
                                1L,
                                7L,
                                10L,
                                "download",
                                List.of("camera-02", "camera-03", "camera-99")
                        )
                )
        );
    }

    @Test
    void missingActionPermissionMappingFailsClosedEvenWithExplicitCameraGrant() {
        ConfiguredReviewCameraPermissionResolver resolver = new ConfiguredReviewCameraPermissionResolver();
        resolver.setUsers(Map.of(7L, List.of("camera-01")));
        resolver.setReviewItemMapper(reviewItemMapper(Map.of(10L, List.of("camera-01"))));

        assertEquals(List.of(), resolver.resolveAllowedCameraIds(
                new ReviewCameraPermissionRequest(1L, 7L, 10L, "download", List.of("camera-01"))));
    }

    @Test
    void explicitUserScopeAllowsOnlyRequestedCamerasPersistedForTenant() {
        ConfiguredReviewCameraPermissionResolver resolver = new ConfiguredReviewCameraPermissionResolver();
        resolver.setUsers(Map.of(7L, List.of("camera-01", "camera-99")));
        resolver.setReviewItemMapper(reviewItemMapper(Map.of(
                10L, List.of("camera-01", "camera-02"),
                20L, List.of("camera-03")
        )));
        resolver.setActionPermissions(Map.of(
                "download", List.of("system:supervision-alert-review:media:download")
        ));
        CapturingPermissionService permissionService = new CapturingPermissionService();
        permissionService.allowed = true;
        resolver.setPermissionService(permissionService);

        List<String> allowed = resolver.resolveAllowedCameraIds(
                new ReviewCameraPermissionRequest(
                        1L,
                        7L,
                        10L,
                        "download",
                        List.of(" camera-01 ", "camera-99", "camera-01")
                )
        );

        assertEquals(List.of("camera-01"), allowed);
        assertEquals(7L, permissionService.checkedUserId);
    }

    @Test
    void dynamicScopeNeverUsesCameraPersistedForAnotherTenant() {
        ConfiguredReviewCameraPermissionResolver resolver = new ConfiguredReviewCameraPermissionResolver();
        resolver.setReviewItemMapper(reviewItemMapper(Map.of(
                20L, List.of("camera-01")
        )));
        resolver.setUsers(Map.of(7L, List.of("camera-01")));
        allowAction(resolver, "playback");

        assertEquals(
                List.of(),
                resolver.resolveAllowedCameraIds(
                        new ReviewCameraPermissionRequest(
                                1L,
                                7L,
                                10L,
                                "playback",
                                List.of("camera-01")
                        )
                )
        );
    }

    @Test
    void dynamicScopeFailsClosedWhenActionPermissionIsDenied() {
        ConfiguredReviewCameraPermissionResolver resolver = new ConfiguredReviewCameraPermissionResolver();
        resolver.setUsers(Map.of(7L, List.of("camera-01")));
        resolver.setReviewItemMapper(reviewItemMapper(Map.of(
                10L, List.of("camera-01")
        )));
        resolver.setActionPermissions(Map.of(
                "export", List.of("system:supervision-alert-review:media:export")
        ));
        resolver.setPermissionService(new CapturingPermissionService());

        assertEquals(
                List.of(),
                resolver.resolveAllowedCameraIds(
                        new ReviewCameraPermissionRequest(
                                1L,
                                7L,
                                10L,
                                "export",
                                List.of("camera-01")
                        )
                )
        );
    }

    @Test
    void explicitUserGrantFailsClosedWithoutTenantCameraEvidence() {
        ConfiguredReviewCameraPermissionResolver resolver = new ConfiguredReviewCameraPermissionResolver();
        resolver.setUsers(Map.of(7L, List.of("database-camera")));
        resolver.setReviewItemMapper(reviewItemMapper(Map.of()));
        allowAction(resolver, "playback");

        assertEquals(
                List.of(),
                resolver.resolveAllowedCameraIds(
                        new ReviewCameraPermissionRequest(
                                1L,
                                7L,
                                10L,
                                "playback",
                                List.of("database-camera")
                        )
                )
        );
    }

    @Test
    void dynamicScopeFailsClosedWithoutAuthenticatedTenantUserMapperOrCameraRecord() {
        ConfiguredReviewCameraPermissionResolver resolver = new ConfiguredReviewCameraPermissionResolver();
        resolver.setUsers(Map.of(7L, List.of("camera-01")));
        allowAction(resolver, "playback");

        assertEquals(List.of(), resolver.resolveAllowedCameraIds(
                new ReviewCameraPermissionRequest(1L, 7L, 10L, "playback", List.of("camera-01"))));

        resolver.setReviewItemMapper(reviewItemMapper(Map.of(10L, List.of("camera-01"))));
        assertEquals(List.of(), resolver.resolveAllowedCameraIds(
                new ReviewCameraPermissionRequest(1L, null, 10L, "playback", List.of("camera-01"))));
        assertEquals(List.of(), resolver.resolveAllowedCameraIds(
                new ReviewCameraPermissionRequest(1L, 7L, null, "playback", List.of("camera-01"))));
        assertEquals(List.of(), resolver.resolveAllowedCameraIds(
                new ReviewCameraPermissionRequest(1L, 7L, 10L, "playback", List.of("camera-99"))));
    }

    @Test
    void explicitlyScopedSuperAdminCanPreviewCameraWithoutReviewHistory() {
        ConfiguredReviewCameraPermissionResolver resolver = new ConfiguredReviewCameraPermissionResolver();
        resolver.setUsers(Map.of(1L, List.of("camera-01")));
        resolver.setActionPermissions(Map.of(
                "playback", List.of("system:supervision-alert-review:media:playback"),
                "alert_read", List.of("system:supervision-alert-review:media:playback"),
                "download", List.of("system:supervision-alert-review:media:download")
        ));
        CapturingPermissionService permissionService = new CapturingPermissionService();
        permissionService.allowed = true;
        permissionService.superAdmin = true;
        resolver.setPermissionService(permissionService);

        assertEquals(List.of("camera-01"), resolver.resolveAllowedCameraIds(
                new ReviewCameraPermissionRequest(1L, 1L, 1L, "playback", List.of("camera-01"))));
        assertEquals(List.of(), resolver.resolveAllowedCameraIds(
                new ReviewCameraPermissionRequest(1L, 1L, 1L, "playback", List.of("camera-02"))));
        assertEquals(List.of(), resolver.resolveAllowedCameraIds(
                new ReviewCameraPermissionRequest(1L, 1L, 1L, "download", List.of("camera-01"))));
        assertEquals(List.of("camera-01"), resolver.resolveAllowedCameraIds(
                new ReviewCameraPermissionRequest(1L, 1L, 1L, "alert_read", List.of())));
    }

    @Test
    void userScopeOverridesTenantAndDefaultAndNormalizesValues() {
        ConfiguredReviewCameraPermissionResolver resolver = new ConfiguredReviewCameraPermissionResolver();
        resolver.setUsers(Map.of(7L, List.of(" camera-01 ", "", "camera-01", "camera-02")));
        resolver.setTenants(Map.of(10L, List.of("tenant-camera")));
        resolver.setDefaultAllowedCameraIds(List.of("default-camera"));
        resolver.setReviewItemMapper(reviewItemMapper(Map.of(10L, List.of("camera-01", "camera-02"))));
        resolver.setActionPermissions(Map.of(
                "playback", List.of("system:supervision-alert-review:media:playback")
        ));
        CapturingPermissionService permissionService = new CapturingPermissionService();
        permissionService.allowed = true;
        resolver.setPermissionService(permissionService);

        List<String> allowed = resolver.resolveAllowedCameraIds(
                new ReviewCameraPermissionRequest(1L, 7L, 10L, "playback", null));

        assertEquals(List.of("camera-01", "camera-02"), allowed);
    }

    @Test
    void tenantAndDefaultScopesNeverReplaceExplicitUserGrant() {
        ConfiguredReviewCameraPermissionResolver tenantResolver = new ConfiguredReviewCameraPermissionResolver();
        tenantResolver.setTenants(Map.of(10L, List.of("tenant-camera")));
        tenantResolver.setReviewItemMapper(reviewItemMapper(Map.of(10L, List.of("tenant-camera"))));
        allowAction(tenantResolver, "export");

        assertEquals(
                List.of(),
                tenantResolver.resolveAllowedCameraIds(
                        new ReviewCameraPermissionRequest(1L, 8L, 10L, "export", null))
        );

        ConfiguredReviewCameraPermissionResolver defaultResolver = new ConfiguredReviewCameraPermissionResolver();
        defaultResolver.setDefaultAllowedCameraIds(List.of("default-camera"));
        defaultResolver.setReviewItemMapper(reviewItemMapper(Map.of(11L, List.of("default-camera"))));
        allowAction(defaultResolver, "download");

        assertEquals(
                List.of(),
                defaultResolver.resolveAllowedCameraIds(
                        new ReviewCameraPermissionRequest(1L, 8L, 11L, "download", null))
        );

        ConfiguredReviewCameraPermissionResolver failClosedResolver = new ConfiguredReviewCameraPermissionResolver();
        failClosedResolver.setReviewItemMapper(reviewItemMapper(Map.of(11L, List.of("camera-01"))));
        allowAction(failClosedResolver, "download");

        assertEquals(
                List.of(),
                failClosedResolver.resolveAllowedCameraIds(
                        new ReviewCameraPermissionRequest(1L, 8L, 11L, "download", null))
        );
        assertEquals(List.of(), failClosedResolver.resolveAllowedCameraIds(
                new ReviewCameraPermissionRequest(1L, null, 11L, "download", null)));
    }

    @Test
    void failOpenConfigurationDoesNotBypassMissingDynamicScopeEvidence() {
        ConfiguredReviewCameraPermissionResolver resolver = new ConfiguredReviewCameraPermissionResolver();
        resolver.setFailClosed(false);
        resolver.setReviewItemMapper(reviewItemMapper(Map.of(11L, List.of("camera-01"))));
        allowAction(resolver, "playback");

        assertEquals(List.of(), resolver.resolveAllowedCameraIds(
                new ReviewCameraPermissionRequest(1L, 8L, 11L, "playback", null)));
    }

    @Test
    void configuredScopesRequireRealActionPermissionWhenPermissionServiceIsPresent() {
        ConfiguredReviewCameraPermissionResolver resolver = new ConfiguredReviewCameraPermissionResolver();
        resolver.setUsers(Map.of(7L, List.of("camera-01")));
        resolver.setReviewItemMapper(reviewItemMapper(Map.of(10L, List.of("camera-01"))));
        resolver.setActionPermissions(Map.of("download", List.of("system:supervision-alert-review:media:download")));
        CapturingPermissionService permissionService = new CapturingPermissionService();
        resolver.setPermissionService(permissionService);

        ReviewCameraPermissionRequest request = new ReviewCameraPermissionRequest(1L, 7L, 10L, "download", null);

        assertEquals(List.of(), resolver.resolveAllowedCameraIds(request));
        assertEquals(7L, permissionService.checkedUserId);
        assertEquals(List.of("system:supervision-alert-review:media:download"), permissionService.checkedPermissions);

        permissionService.allowed = true;

        assertEquals(List.of("camera-01"), resolver.resolveAllowedCameraIds(request));
    }

    @Test
    void actionPermissionKeysAreNormalizedBeforeGateLookup() {
        ConfiguredReviewCameraPermissionResolver resolver = new ConfiguredReviewCameraPermissionResolver();
        resolver.setUsers(Map.of(7L, List.of("camera-01")));
        resolver.setActionPermissions(Map.of(" Download ", List.of("system:supervision-alert-review:media:download")));
        CapturingPermissionService permissionService = new CapturingPermissionService();
        resolver.setPermissionService(permissionService);

        assertEquals(List.of(), resolver.resolveAllowedCameraIds(
                new ReviewCameraPermissionRequest(1L, 7L, 10L, "download", null)));
        assertEquals(List.of("system:supervision-alert-review:media:download"), permissionService.checkedPermissions);
    }

    private static SupervisionAlertReviewItemMapper reviewItemMapper(Map<Long, List<String>> camerasByTenant) {
        Map<Long, List<String>> persistedCameras = new LinkedHashMap<>(camerasByTenant);
        return (SupervisionAlertReviewItemMapper) Proxy.newProxyInstance(
                SupervisionAlertReviewItemMapper.class.getClassLoader(),
                new Class<?>[]{SupervisionAlertReviewItemMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "ConfiguredReviewCameraPermissionResolverTest.ReviewItemMapper";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    if ("selectExistingCameraIds".equals(method.getName())) {
                        Long tenantId = (Long) args[0];
                        @SuppressWarnings("unchecked")
                        List<String> requestedCameraIds = (List<String>) args[1];
                        return persistedCameras.getOrDefault(tenantId, List.of()).stream()
                                .filter(requestedCameraIds::contains)
                                .distinct()
                                .toList();
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static SupervisionAlertReviewItemMapper reviewItemMapperThatMustNotBeCalled() {
        return (SupervisionAlertReviewItemMapper) Proxy.newProxyInstance(
                SupervisionAlertReviewItemMapper.class.getClassLoader(),
                new Class<?>[]{SupervisionAlertReviewItemMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "ConfiguredReviewCameraPermissionResolverTest.UnusedReviewItemMapper";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    if ("selectExistingCameraIds".equals(method.getName())) {
                        throw new AssertionError("alert_read must not query review history");
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static void allowAction(ConfiguredReviewCameraPermissionResolver resolver, String action) {
        resolver.setActionPermissions(Map.of(
                action,
                List.of("system:supervision-alert-review:media:" + action)
        ));
        CapturingPermissionService permissionService = new CapturingPermissionService();
        permissionService.allowed = true;
        resolver.setPermissionService(permissionService);
    }

    private static final class CapturingPermissionService implements PermissionService {

        private boolean allowed;
        private boolean superAdmin;
        private Long checkedUserId;
        private List<String> checkedPermissions = List.of();

        @Override
        public boolean hasAnyPermissions(Long userId, String... permissions) {
            checkedUserId = userId;
            checkedPermissions = permissions == null ? List.of() : List.of(permissions);
            return allowed;
        }

        @Override
        public boolean hasAnyRoles(Long userId, String... roles) {
            return superAdmin;
        }

        @Override
        public void assignRoleMenu(Long roleId, Set<Long> menuIds) {
        }

        @Override
        public void processRoleDeleted(Long roleId) {
        }

        @Override
        public void processMenuDeleted(Long menuId) {
        }

        @Override
        public Set<Long> getRoleMenuListByRoleId(Collection<Long> roleIds) {
            return Set.of();
        }

        @Override
        public Set<Long> getMenuRoleIdListByMenuIdFromCache(Long menuId) {
            return Set.of();
        }

        @Override
        public void assignUserRole(Long userId, Set<Long> roleIds) {
        }

        @Override
        public void processUserDeleted(Long userId) {
        }

        @Override
        public Set<Long> getUserRoleIdListByRoleId(Collection<Long> roleIds) {
            return Set.of();
        }

        @Override
        public Set<Long> getUserRoleIdListByUserId(Long userId) {
            return Set.of();
        }

        @Override
        public Set<Long> getUserRoleIdListByUserIdFromCache(Long userId) {
            return Set.of();
        }

        @Override
        public void assignRoleDataScope(Long roleId, Integer dataScope, Set<Long> dataScopeDeptIds) {
        }

        @Override
        public DeptDataPermissionRespDTO getDeptDataPermission(Long userId) {
            return null;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ConfiguredReviewCameraPermissionResolver.class)
    static class CameraPermissionBindingConfiguration {
    }
}
