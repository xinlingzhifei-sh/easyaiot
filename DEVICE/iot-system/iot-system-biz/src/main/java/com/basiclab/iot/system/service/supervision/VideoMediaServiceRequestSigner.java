package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.common.core.context.TenantContextHolder;
import com.basiclab.iot.common.domain.LoginUser;
import com.basiclab.iot.common.utils.SecurityFrameworkUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

@Service
public class VideoMediaServiceRequestSigner implements ReviewPlaybackUrlSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String secret;
    private final String serviceId;
    private final Supplier<String> userIdSupplier;
    private final Supplier<String> tenantIdSupplier;
    private final LongSupplier epochSecondSupplier;
    private final Supplier<String> nonceSupplier;
    private final BiPredicate<String, String> scopeVerifier;

    @Autowired
    public VideoMediaServiceRequestSigner(
            @Value("${yfeieye.video.media-service-hmac-secret:}") String secret,
            @Value("${yfeieye.video.media-service-id:iot-system}") String serviceId,
            @Value("${yfeieye.video.media-service-allowed-actions:coverage,export,download,playback,manifest_verify}") String serviceAllowedActions,
            @Value("${yfeieye.video.media-service-allowed-camera-ids:}") String serviceAllowedCameraIds,
            ConfiguredReviewCameraPermissionResolver cameraPermissionResolver) {
        this(secret, serviceId,
                () -> currentActorId(serviceId),
                VideoMediaServiceRequestSigner::currentTenantId,
                () -> Instant.now().getEpochSecond(),
                () -> UUID.randomUUID().toString(),
                (action, cameraId) -> verifyConfiguredScope(
                        cameraPermissionResolver,
                        csvValues(serviceAllowedActions, true),
                        csvValues(serviceAllowedCameraIds, false),
                        action,
                        cameraId
                ));
    }

    public VideoMediaServiceRequestSigner(String secret,
                                          String serviceId,
                                          Supplier<String> userIdSupplier,
                                          Supplier<String> tenantIdSupplier,
                                          LongSupplier epochSecondSupplier,
                                          Supplier<String> nonceSupplier,
                                          BiPredicate<String, String> scopeVerifier) {
        this.secret = normalize(secret);
        this.serviceId = normalize(serviceId);
        this.userIdSupplier = userIdSupplier;
        this.tenantIdSupplier = tenantIdSupplier;
        this.epochSecondSupplier = epochSecondSupplier;
        this.nonceSupplier = nonceSupplier;
        this.scopeVerifier = scopeVerifier;
    }

    public HttpHeaders sign(HttpMethod method,
                            URI uri,
                            String action,
                            String cameraId,
                            String body) {
        if (secret.isEmpty()) {
            throw new IllegalStateException("yfeieye.video.media-service-hmac-secret is not configured");
        }
        if (serviceId.isEmpty()) {
            throw new IllegalStateException("yfeieye.video.media-service-id is not configured");
        }
        String userId = normalize(userIdSupplier.get());
        String tenantId = normalize(tenantIdSupplier.get());
        String normalizedAction = normalize(action).toLowerCase(Locale.ROOT);
        String normalizedCameraId = normalize(cameraId);
        if (userId.isEmpty()) {
            throw new IllegalStateException("trusted media actor is missing");
        }
        if (tenantId.isEmpty()) {
            throw new IllegalStateException("trusted media tenant is missing");
        }
        if (normalizedAction.isEmpty()) {
            throw new IllegalStateException("trusted media action is missing");
        }
        if (normalizedCameraId.isEmpty()) {
            throw new IllegalStateException("trusted media camera is missing");
        }
        if (scopeVerifier == null || !scopeVerifier.test(normalizedAction, normalizedCameraId)) {
            throw new SecurityException("DEVICE media action or camera scope denied");
        }
        String timestamp = String.valueOf(epochSecondSupplier.getAsLong());
        String nonce = normalize(nonceSupplier.get());
        if (nonce.isEmpty()) {
            throw new IllegalStateException("trusted media nonce is missing");
        }
        String requestBody = body == null ? "" : body;
        String signature = signature(
                method == null ? "" : method.name(),
                canonicalTarget(uri),
                timestamp,
                nonce,
                serviceId,
                userId,
                tenantId,
                normalizedCameraId,
                normalizedAction,
                requestBody,
                secret
        );
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-YFeiEye-Service-Id", serviceId);
        headers.set("X-YFeiEye-Service-User-Id", userId);
        headers.set("X-YFeiEye-Service-Tenant-Id", tenantId);
        headers.set("X-YFeiEye-Service-Camera-Id", normalizedCameraId);
        headers.set("X-YFeiEye-Service-Action", normalizedAction);
        headers.set("X-YFeiEye-Service-Timestamp", timestamp);
        headers.set("X-YFeiEye-Service-Nonce", nonce);
        headers.set("X-YFeiEye-Service-Signature", signature);
        return headers;
    }

    /**
     * Creates a short-lived, camera-scoped URL for native browser media loads.
     * Native video/img elements cannot attach the DEVICE bearer or service HMAC
     * headers, so the same signed context is appended as query parameters. VIDEO
     * validates it against the original target query and its timestamp window.
     */
    @Override
    public String signPlaybackUrl(String rawUrl, String cameraId) {
        URI target = ensureSeekablePlaybackTarget(rawUrl);
        URI videoTarget = canonicalVideoPlaybackTarget(target);
        String path = normalize(videoTarget.getPath());
        if (!path.startsWith("/video/record/") && !"/video/alert/record".equals(path)) {
            throw new IllegalArgumentException("playback URL must target a VIDEO record endpoint");
        }
        HttpHeaders headers = sign(HttpMethod.GET, videoTarget, "playback", cameraId, "");
        Map<String, String> ticket = new LinkedHashMap<>();
        ticket.put("yf_ticket", "v1");
        ticket.put("yf_service_id", headers.getFirst("X-YFeiEye-Service-Id"));
        ticket.put("yf_user_id", headers.getFirst("X-YFeiEye-Service-User-Id"));
        ticket.put("yf_tenant_id", headers.getFirst("X-YFeiEye-Service-Tenant-Id"));
        ticket.put("yf_camera_id", headers.getFirst("X-YFeiEye-Service-Camera-Id"));
        ticket.put("yf_action", headers.getFirst("X-YFeiEye-Service-Action"));
        ticket.put("yf_timestamp", headers.getFirst("X-YFeiEye-Service-Timestamp"));
        ticket.put("yf_nonce", headers.getFirst("X-YFeiEye-Service-Nonce"));
        ticket.put("yf_signature", headers.getFirst("X-YFeiEye-Service-Signature"));
        StringBuilder result = new StringBuilder(target.toASCIIString());
        result.append(target.getRawQuery() == null ? '?' : '&');
        ticket.forEach((key, value) -> {
            if (result.charAt(result.length() - 1) != '?' && result.charAt(result.length() - 1) != '&') {
                result.append('&');
            }
            result.append(urlEncode(key)).append('=').append(urlEncode(value));
        });
        return result.toString();
    }

    private static URI canonicalVideoPlaybackTarget(URI target) {
        String rawPath = normalize(target.getRawPath());
        int videoPathIndex = rawPath.indexOf("/video/");
        if (videoPathIndex < 0) {
            throw new IllegalArgumentException("playback URL must target a VIDEO record endpoint");
        }
        String videoPath = rawPath.substring(videoPathIndex);
        String rawQuery = normalize(target.getRawQuery());
        return URI.create(videoPath + (rawQuery.isEmpty() ? "" : "?" + rawQuery));
    }

    private static URI ensureSeekablePlaybackTarget(String rawUrl) {
        URI uri = URI.create(normalize(rawUrl));
        if (uri.getRawFragment() != null) {
            throw new IllegalArgumentException("playback URL fragment is not allowed");
        }
        String query = Arrays.stream(normalize(uri.getRawQuery()).split("&"))
                .filter(value -> !value.isBlank())
                .filter(value -> !"playback_format".equals(value.split("=", 2)[0]))
                .collect(java.util.stream.Collectors.joining("&"));
        String base = uri.toASCIIString();
        int queryIndex = base.indexOf('?');
        if (queryIndex >= 0) {
            base = base.substring(0, queryIndex);
        }
        return URI.create(base + "?" + (query.isEmpty() ? "" : query + "&") + "playback_format=mp4");
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(normalize(value), StandardCharsets.UTF_8);
    }

    private static String canonicalTarget(URI uri) {
        if (uri == null) {
            return "";
        }
        String path = normalize(uri.getRawPath());
        String query = normalize(uri.getRawQuery());
        return query.isEmpty() ? path : path + "?" + query;
    }

    private static String currentActorId(String serviceId) {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        return loginUser != null && loginUser.getId() != null
                ? String.valueOf(loginUser.getId())
                : "service:" + normalize(serviceId);
    }

    private static String currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
            tenantId = loginUser == null ? null : loginUser.getTenantId();
        }
        return tenantId == null ? null : String.valueOf(tenantId);
    }

    private static String signature(String method,
                                    String path,
                                    String timestamp,
                                    String nonce,
                                    String serviceId,
                                    String userId,
                                    String tenantId,
                                    String cameraId,
                                    String action,
                                    String body,
                                    String secret) {
        String bodyHash = HexFormat.of().formatHex(sha256(body.getBytes(StandardCharsets.UTF_8)));
        String canonical = String.join("\n",
                "v1",
                timestamp,
                nonce,
                method.toUpperCase(),
                path,
                serviceId,
                userId,
                tenantId,
                cameraId,
                action.toLowerCase(Locale.ROOT),
                bodyHash
        );
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("cannot sign VIDEO media request", exception);
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean verifyConfiguredScope(ConfiguredReviewCameraPermissionResolver resolver,
                                                 Set<String> serviceAllowedActions,
                                                 Set<String> serviceAllowedCameraIds,
                                                 String action,
                                                 String cameraId) {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null && loginUser != null) {
            tenantId = loginUser.getTenantId();
        }
        if (loginUser != null && loginUser.getId() != null && tenantId != null) {
            List<String> allowedCameraIds = resolver.resolveAllowedCameraIds(
                    new SupervisionAlertReviewService.ReviewCameraPermissionRequest(
                            null,
                            loginUser.getId(),
                            tenantId,
                            action,
                            List.of(cameraId)
                    )
            );
            return allowedCameraIds != null && allowedCameraIds.stream().anyMatch(cameraId::equals);
        }
        return serviceAllowedActions.contains(action)
                && (serviceAllowedCameraIds.contains("*") || serviceAllowedCameraIds.contains(cameraId));
    }

    private static Set<String> csvValues(String value, boolean lowercase) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(item -> lowercase ? item.toLowerCase(Locale.ROOT) : item)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
