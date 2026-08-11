package com.basiclab.iot.sink.controller;

import com.basiclab.iot.common.domain.CommonResult;
import com.basiclab.iot.sink.service.media.DvrUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.security.PermitAll;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * SRS/ZLM DVR Hook（录像上传 MinIO + Playback/告警回填），替代 VIDEO dvr_upload_service。
 */
@Slf4j
@Tag(name = "媒体 Hook")
@RestController
@RequestMapping("/media/hook")
public class MediaHookController {

    static final String TOKEN_HEADER = "X-YFeiEye-Hook-Token";
    private static final int MIN_TOKEN_LENGTH = 32;

    private final DvrUploadService dvrUploadService;
    private final String configuredToken;

    public MediaHookController(
            DvrUploadService dvrUploadService,
            @Value("${YFEIEYE_SRS_HOOK_TOKEN:}") String configuredToken) {
        this.dvrUploadService = dvrUploadService;
        this.configuredToken = configuredToken == null ? "" : configuredToken;
    }

    @PostMapping("/srs/on_dvr")
    @PermitAll
    @Operation(summary = "SRS DVR 回调：从 NFS 读录像并上传 MinIO")
    public Map<String, Object> srsOnDvr(
            @RequestHeader(value = TOKEN_HEADER, required = false) String suppliedToken,
            @RequestBody Map<String, Object> body) {
        verifyToken(suppliedToken);
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 0);
        try {
            boolean ok = dvrUploadService.processDvrEvent(body);
            resp.put("msg", ok ? "success" : "skipped_or_failed");
        } catch (Exception e) {
            log.error("SRS on_dvr 处理失败: {}", e.getMessage(), e);
            resp.put("code", -1);
            resp.put("msg", e.getMessage());
        }
        return resp;
    }

    @PostMapping("/zlm/on_record_mp4")
    @PermitAll
    @Operation(summary = "ZLM MP4 录像回调")
    public Map<String, Object> zlmOnRecordMp4(
            @RequestHeader(value = TOKEN_HEADER, required = false) String suppliedToken,
            @RequestBody Map<String, Object> body) {
        if (body != null && !body.containsKey("file_path") && body.containsKey("file")) {
            body.put("file_path", body.get("file"));
        }
        return srsOnDvr(suppliedToken, body);
    }

    @PostMapping("/health")
    @PermitAll
    public CommonResult<String> health() {
        return CommonResult.success("ok");
    }

    private void verifyToken(String suppliedToken) {
        if (configuredToken.length() < MIN_TOKEN_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "YFEIEYE_SRS_HOOK_TOKEN is not configured");
        }
        if (suppliedToken == null || !MessageDigest.isEqual(
                configuredToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid media hook token");
        }
    }
}
