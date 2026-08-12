package com.basiclab.iot.system.controller.admin.auth;



import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.basiclab.iot.common.enums.CommonStatusEnum;
import com.basiclab.iot.common.enums.UserTypeEnum;
import com.basiclab.iot.common.domain.CommonResult;
import com.basiclab.iot.common.config.SecurityProperties;
import com.basiclab.iot.common.utils.SecurityFrameworkUtils;
import com.basiclab.iot.system.controller.admin.auth.vo.*;
import com.basiclab.iot.system.convert.auth.AuthConvert;
import com.basiclab.iot.system.dal.dataobject.permission.MenuDO;
import com.basiclab.iot.system.dal.dataobject.permission.RoleDO;
import com.basiclab.iot.system.dal.dataobject.user.AdminUserDO;
import com.basiclab.iot.system.enums.logger.LoginLogTypeEnum;
import com.basiclab.iot.system.service.auth.AdminAuthService;
import com.basiclab.iot.system.service.permission.MenuService;
import com.basiclab.iot.system.service.permission.PermissionService;
import com.basiclab.iot.system.service.permission.RoleService;
import com.basiclab.iot.system.service.user.AdminUserService;
import com.basiclab.iot.system.service.supervision.ConfiguredReviewCameraPermissionResolver;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCameraPermissionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Locale;

import static com.basiclab.iot.common.domain.CommonResult.success;
import static com.basiclab.iot.common.utils.collection.CollectionUtils.convertSet;
import static com.basiclab.iot.common.utils.SecurityFrameworkUtils.getLoginUserId;

/**
 * AuthController
 *
 * @author reese
 * @email reese
 */
@Tag(name = "管理后台 - 认证")
@RestController
@RequestMapping("/system/auth")
@Validated
@Slf4j
public class AuthController {

    private static final Set<String> MEDIA_ACTIONS = Set.of(
            "playback", "snapshot", "coverage", "export", "download", "manifest_verify", "record_manage",
            "alert_read"
    );
    private static final Set<String> MEDIA_COLLECTION_ACTIONS = Set.of("alert_read");

    @Resource
    private AdminAuthService authService;
    @Resource
    private AdminUserService userService;
    @Resource
    private RoleService roleService;
    @Resource
    private MenuService menuService;
    @Resource
    private PermissionService permissionService;
    @Resource
    private ConfiguredReviewCameraPermissionResolver reviewCameraPermissionResolver;

    @Resource
    private SecurityProperties securityProperties;

    @PostMapping("/login")
    @PermitAll
    @Operation(summary = "使用账号密码登录")
    public CommonResult<AuthLoginRespVO> login(@RequestBody @Valid AuthLoginReqVO reqVO) {
        return success(authService.login(reqVO));
    }

    @PostMapping("/logout")
    @PermitAll
    @Operation(summary = "登出系统")
    public CommonResult<Boolean> logout(HttpServletRequest request) {
        String token = SecurityFrameworkUtils.obtainAuthorization(request,
                securityProperties.getTokenHeader(), securityProperties.getTokenParameter());
        if (StrUtil.isNotBlank(token)) {
            authService.logout(token, LoginLogTypeEnum.LOGOUT_SELF.getType());
        }
        return success(true);
    }

    @PostMapping("/refresh-token")
    @PermitAll
    @Operation(summary = "刷新令牌")
    @Parameter(name = "refreshToken", description = "刷新令牌", required = true)
    public CommonResult<AuthLoginRespVO> refreshToken(@RequestParam("refreshToken") String refreshToken) {
        return success(authService.refreshToken(refreshToken));
    }

    @GetMapping("/get-permission-info")
    @Operation(summary = "获取登录用户的权限信息")
    public CommonResult<AuthPermissionInfoRespVO> getPermissionInfo() {
        // 1.1 获得用户信息
        AdminUserDO user = userService.getUser(getLoginUserId());
        if (user == null) {
            return success(null);
        }

        // 1.2 获得角色列表
        Set<Long> roleIds = permissionService.getUserRoleIdListByUserId(getLoginUserId());
        if (CollUtil.isEmpty(roleIds)) {
            return success(AuthConvert.INSTANCE.convert(user, Collections.emptyList(), Collections.emptyList()));
        }
        List<RoleDO> roles = roleService.getRoleList(roleIds);
        roles.removeIf(role -> !CommonStatusEnum.ENABLE.getStatus().equals(role.getStatus())); // 移除禁用的角色

        // 1.3 获得菜单列表
        Set<Long> menuIds = permissionService.getRoleMenuListByRoleId(convertSet(roles, RoleDO::getId));
        List<MenuDO> menuList = menuService.getMenuList(menuIds);
        menuList.removeIf(menu -> !CommonStatusEnum.ENABLE.getStatus().equals(menu.getStatus())); // 移除禁用的菜单

        // 2. 拼接结果返回
        return success(AuthConvert.INSTANCE.convert(user, roles, menuList));
    }

    @GetMapping("/check-session")
    @Operation(summary = "校验当前登录会话")
    public ResponseEntity<Void> checkSession() {
        if (getLoginUserId() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/media-permission-check")
    @Operation(summary = "Resolve authenticated tenant, action permission and camera scope for VIDEO")
    public CommonResult<MediaPermissionCheckRespVO> checkMediaPermission(
            @RequestBody(required = false) MediaPermissionCheckReqVO reqVO) {
        MediaPermissionCheckReqVO request = reqVO == null ? new MediaPermissionCheckReqVO() : reqVO;
        Long userId = getLoginUserId();
        AdminUserDO user = userId == null ? null : userService.getUser(userId);
        Long tenantId = user == null ? null : user.getTenantId();
        String action = normalizeMediaValue(request.getAction()).toLowerCase(Locale.ROOT);
        String cameraId = normalizeMediaValue(request.getCameraId());
        if (userId == null || user == null) {
            return success(mediaPermissionDecision(false, userId, tenantId, cameraId, action,
                    "authentication_required"));
        }
        if (tenantId == null) {
            return success(mediaPermissionDecision(false, userId, null, cameraId, action,
                    "tenant_required"));
        }
        if (!MEDIA_ACTIONS.contains(action)) {
            return success(mediaPermissionDecision(false, userId, tenantId, cameraId, action,
                    "action_permission_denied"));
        }
        if (cameraId.isEmpty() && !MEDIA_COLLECTION_ACTIONS.contains(action)) {
            return success(mediaPermissionDecision(false, userId, tenantId, null, action,
                    "camera_scope_required"));
        }
        List<String> allowedCameraIds = reviewCameraPermissionResolver.resolveAllowedCameraIds(
                new ReviewCameraPermissionRequest(
                        null, userId, tenantId, action, cameraId.isEmpty() ? List.of() : List.of(cameraId))
        );
        if (cameraId.isEmpty()) {
            if (allowedCameraIds == null || allowedCameraIds.isEmpty()) {
                return success(mediaPermissionDecision(false, userId, tenantId, null, action,
                        "camera_scope_denied"));
            }
            if (allowedCameraIds.size() != 1) {
                return success(mediaPermissionDecision(false, userId, tenantId, null, action,
                        "camera_scope_ambiguous"));
            }
            return success(mediaPermissionDecision(
                    true, userId, tenantId, allowedCameraIds.get(0), action, "granted"));
        }
        boolean allowed = allowedCameraIds != null && allowedCameraIds.stream().anyMatch(cameraId::equals);
        return success(mediaPermissionDecision(
                allowed,
                userId,
                tenantId,
                cameraId,
                action,
                allowed ? "granted" : "camera_scope_denied"
        ));
    }

    private static MediaPermissionCheckRespVO mediaPermissionDecision(boolean allowed,
                                                                       Long userId,
                                                                       Long tenantId,
                                                                       String cameraId,
                                                                       String action,
                                                                       String reason) {
        return new MediaPermissionCheckRespVO(allowed, userId, tenantId, cameraId, action, reason);
    }

    private static String normalizeMediaValue(String value) {
        return value == null ? "" : value.trim();
    }

    // ========== 短信登录相关 ==========

    @PostMapping("/sms-login")
    @PermitAll
    @Operation(summary = "使用短信验证码登录")
    public CommonResult<AuthLoginRespVO> smsLogin(@RequestBody @Valid AuthSmsLoginReqVO reqVO) {
        return success(authService.smsLogin(reqVO));
    }

    @PostMapping("/send-sms-code")
    @PermitAll
    @Operation(summary = "发送手机验证码")
    public CommonResult<Boolean> sendLoginSmsCode(@RequestBody @Valid AuthSmsSendReqVO reqVO) {
        authService.sendSmsCode(reqVO);
        return success(true);
    }

}
