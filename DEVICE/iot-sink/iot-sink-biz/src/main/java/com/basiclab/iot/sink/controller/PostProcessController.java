package com.basiclab.iot.sink.controller;

import com.basiclab.iot.common.domain.CommonResult;
import com.basiclab.iot.sink.domain.model.PostProcessRequestMessage;
import com.basiclab.iot.sink.service.PostProcessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.security.PermitAll;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 算法后处理入队 API（VIDEO 算法检测侧 HTTP 投递，由 iot-sink 对接 Kafka）
 */
@Tag(name = "算法后处理")
@RestController
@RequestMapping("/post-process")
public class PostProcessController {

    static final String TOKEN_HEADER = "X-Iot-Sink-Token";
    private static final int MIN_TOKEN_LENGTH = 32;

    private final PostProcessService postProcessService;
    private final String configuredToken;

    public PostProcessController(
            PostProcessService postProcessService,
            @Value("${IOT_SINK_POST_PROCESS_TOKEN:}") String configuredToken) {
        this.postProcessService = postProcessService;
        this.configuredToken = configuredToken == null ? "" : configuredToken;
    }

    @PostMapping("/enqueue")
    @PermitAll
    @Operation(summary = "后处理请求入队（写入 Kafka request 主题）")
    public CommonResult<Boolean> enqueue(
            @RequestHeader(value = TOKEN_HEADER, required = false) String suppliedToken,
            @RequestBody PostProcessRequestMessage message) {
        verifyToken(suppliedToken);
        postProcessService.enqueue(message);
        return CommonResult.success(true);
    }

    private void verifyToken(String suppliedToken) {
        if (configuredToken.length() < MIN_TOKEN_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "IOT_SINK_POST_PROCESS_TOKEN is not configured");
        }
        if (suppliedToken == null || !MessageDigest.isEqual(
                configuredToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid service token");
        }
    }
}
