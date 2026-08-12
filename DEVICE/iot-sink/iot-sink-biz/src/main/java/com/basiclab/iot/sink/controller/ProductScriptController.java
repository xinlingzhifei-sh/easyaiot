package com.basiclab.iot.sink.controller;

import cn.hutool.core.util.StrUtil;
import com.basiclab.iot.common.domain.R;
import com.basiclab.iot.common.utils.json.JsonUtils;
import com.basiclab.iot.device.RemoteProductService;
import com.basiclab.iot.device.domain.device.vo.Product;
import com.basiclab.iot.device.enums.device.ProtocolTypeEnum;
import com.basiclab.iot.sink.controller.vo.ProductScriptSimulateReqVO;
import com.basiclab.iot.sink.controller.vo.ProductScriptSimulateRespVO;
import com.basiclab.iot.sink.dal.dataobject.ProductScriptDO;
import com.basiclab.iot.sink.dal.mapper.ProductScriptMapper;
import com.basiclab.iot.sink.javascript.JsScriptManager;
import com.basiclab.iot.sink.javascript.JsUtilFunction;
import com.basiclab.iot.sink.javascript.ProductScriptTemplates;
import com.basiclab.iot.sink.messagebus.core.IotMessageBus;
import com.basiclab.iot.sink.service.product.ProductScriptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.script.ScriptException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 产品 JS 编解码脚本管理。
 * <p>
 * 标准 JSON（Topic Codec）可不配脚本；私有协议需配置 rawDataToProtocol / protocolToRawData。
 * Modbus / OPC UA 等工业轮询协议由 sink 直连采集，禁止配置协议脚本。
 */
@Tag(name = "产品协议脚本")
@RestController
@RequestMapping("/product-script")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("@ss.hasRole('super_admin')")
public class ProductScriptController {

    private final ProductScriptService productScriptService;
    private final ProductScriptMapper productScriptMapper;
    private final JsScriptManager jsScriptManager;
    private final RemoteProductService remoteProductService;
    private final JsUtilFunction jsUtilFunction = new JsUtilFunction();

    @Resource
    @Lazy
    private IotMessageBus messageBus;

    private static final String SCRIPT_CHANGE_TOPIC = "iot_product_script_change";

    @Operation(summary = "列出内置脚本模板")
    @GetMapping("/meta/templates")
    public R<List<Map<String, String>>> templates() {
        List<Map<String, String>> list = new ArrayList<>();
        list.add(templateItem("skeleton", "默认骨架（推荐）",
                "固定 rawDataToProtocol / protocolToRawData 入口，在此填写私有协议逻辑", ProductScriptTemplates.SKELETON));
        list.add(templateItem("compact_text", "紧凑文本协议 EA|…（推荐演示）",
                "mqtt-demo 04/05 使用的私有文本协议，易读易调", ProductScriptTemplates.COMPACT_TEXT));
        list.add(templateItem("binary", "二进制协议 ReadBuffer/WriteBuffer",
                "适合真正二进制帧；前端模拟可用 Hex 输入", ProductScriptTemplates.BINARY));
        list.add(templateItem("passthrough", "透传（标准 JSON）",
                "设备已发标准 JSON，脚本原样返回；一般无需启用", ProductScriptTemplates.PASSTHROUGH));
        return R.ok(list);
    }

    @Operation(summary = "查询当前进程已热加载的产品脚本")
    @GetMapping("/meta/loaded")
    public R<Map<String, Object>> loaded() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("products", jsScriptManager.getAllLoadedProductIdentifications());
        map.put("count", jsScriptManager.getAllLoadedProductIdentifications().size());
        return R.ok(map);
    }

    @Operation(summary = "按产品标识查询脚本（含未启用）")
    @GetMapping("/{productIdentification}")
    public R<ProductScriptDO> get(@PathVariable String productIdentification) {
        String reject = rejectIndustrialScript(productIdentification);
        if (reject != null) {
            return R.fail(reject);
        }
        return R.ok(productScriptMapper.selectByProductIdentification(productIdentification));
    }

    @Operation(summary = "保存或更新脚本，并热加载到引擎")
    @PostMapping
    public R<?> save(@RequestBody ProductScriptDO script) throws ScriptException {
        if (script == null || StrUtil.isBlank(script.getProductIdentification())) {
            return R.fail("productIdentification 不能为空");
        }
        String reject = rejectIndustrialScript(script.getProductIdentification());
        if (reject != null) {
            return R.fail(reject);
        }
        if (StrUtil.isNotBlank(script.getScriptContent())) {
            JsScriptManager.CheckResult checkResult = jsScriptManager.checkScript(script.getScriptContent());
            if (!checkResult.isSuccess()) {
                return R.fail("脚本校验失败: " + checkResult.getMessage());
            }
        }

        ProductScriptDO existing = productScriptMapper.selectByProductIdentification(script.getProductIdentification());
        if (existing != null) {
            script.setId(existing.getId());
            if (script.getProductId() == null) {
                script.setProductId(existing.getProductId());
            }
            script.setScriptVersion(existing.getScriptVersion() == null ? 1 : existing.getScriptVersion() + 1);
            if (script.getTenantId() == null) {
                script.setTenantId(existing.getTenantId());
            }
        } else if (script.getScriptVersion() == null) {
            script.setScriptVersion(1);
        }
        if (script.getCreateTime() == null) {
            script.setCreateTime(LocalDateTime.now());
        }
        script.setUpdateTime(LocalDateTime.now());
        productScriptService.saveOrUpdate(script);

        if (Boolean.TRUE.equals(script.getScriptEnabled()) && StrUtil.isNotBlank(script.getScriptContent())) {
            jsScriptManager.addScript(script.getProductIdentification(), script.getScriptContent());
        } else {
            jsScriptManager.removeScript(script.getProductIdentification());
        }
        publishChange(script.getProductId(), script.getProductIdentification(), "update");
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("loaded", jsScriptManager.hasScript(script.getProductIdentification()));
        resp.put("scriptVersion", script.getScriptVersion());
        return R.ok(resp);
    }

    @Operation(summary = "删除脚本")
    @DeleteMapping("/{productId}")
    public R<Boolean> delete(@PathVariable Long productId,
                             @RequestParam(required = false) String productIdentification) {
        ProductScriptDO existing = productScriptMapper.selectByProductId(productId);
        productScriptService.deleteByProductId(productId);
        String pid = StrUtil.blankToDefault(productIdentification,
                existing != null ? existing.getProductIdentification() : null);
        if (StrUtil.isNotBlank(pid)) {
            jsScriptManager.removeScript(pid);
        }
        publishChange(productId, pid, "delete");
        return R.ok(true);
    }

    @Operation(summary = "校验脚本语法与约定函数")
    @PostMapping("/check")
    public R<?> check(@RequestBody Map<String, String> body) {
        String content = body != null ? body.get("scriptContent") : null;
        if (StrUtil.isBlank(content)) {
            return R.fail("scriptContent 不能为空");
        }
        JsScriptManager.CheckResult checkResult = jsScriptManager.checkScript(content);
        if (!checkResult.isSuccess()) {
            return R.fail("脚本校验失败: " + checkResult.getMessage());
        }
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("passed", true);
        ok.put("message", checkResult.getMessage());
        ok.put("loadedProducts", jsScriptManager.getAllLoadedProductIdentifications());
        return R.ok(ok);
    }

    @Operation(summary = "模拟调试：上行解码 / 下行编码（不落库）")
    @PostMapping("/simulate")
    public R<ProductScriptSimulateRespVO> simulate(@RequestBody ProductScriptSimulateReqVO req) {
        if (req == null || StrUtil.isBlank(req.getScriptContent())) {
            return R.fail("scriptContent 不能为空");
        }
        String direction = StrUtil.blankToDefault(req.getDirection(), "uplink");
        String topic = StrUtil.blankToDefault(req.getTopic(), "/iot/demo/demo/property/upstream/report");

        byte[] raw = null;
        if (StrUtil.isNotBlank(req.getPayloadHex())) {
            try {
                raw = jsUtilFunction.fromHex(req.getPayloadHex());
            } catch (Exception e) {
                return R.fail("payloadHex 无效: " + e.getMessage());
            }
        } else if (req.getPayloadText() != null) {
            raw = req.getPayloadText().getBytes(StandardCharsets.UTF_8);
        }

        JsScriptManager.SimulateResult result = jsScriptManager.simulate(
                req.getScriptContent(), direction, topic, raw, req.getMessage());

        byte[] out = result.getOutput();
        String outText = new String(out, StandardCharsets.UTF_8);
        Map<String, Object> outJson = null;
        try {
            if (StrUtil.isNotBlank(outText) && (outText.trim().startsWith("{") || outText.trim().startsWith("["))) {
                outJson = JsonUtils.parseObject(outText, Map.class);
            }
        } catch (Exception ignored) {
            // 非 JSON 输出正常（如下行私有协议）
        }

        ProductScriptSimulateRespVO resp = ProductScriptSimulateRespVO.builder()
                .success(result.isSuccess())
                .message(result.getMessage())
                .direction(direction)
                .elapsedMs(result.getElapsedMs())
                .outputText(outText)
                .outputHex(jsUtilFunction.toHex(out))
                .outputJson(outJson)
                .outputLength(out.length)
                .build();
        if (!result.isSuccess()) {
            return R.fail(resp, result.getMessage());
        }
        return R.ok(resp);
    }

    /**
     * @return 拒绝原因；允许配置脚本时返回 null
     */
    private String rejectIndustrialScript(String productIdentification) {
        if (StrUtil.isBlank(productIdentification) || remoteProductService == null) {
            return null;
        }
        try {
            R<Product> result = remoteProductService.selectByProductIdentification(productIdentification);
            if (result == null || !result.isSuccess() || result.getData() == null) {
                return null;
            }
            String protocolType = result.getData().getProtocolType();
            if (ProtocolTypeEnum.isIndustrial(protocolType)) {
                return "工业轮询协议（" + protocolType + "）由网关直连采集点位，无需也不支持配置协议脚本";
            }
        } catch (Exception e) {
            log.warn("[rejectIndustrialScript][查询产品协议类型失败 product={}]: {}",
                    productIdentification, e.getMessage());
        }
        return null;
    }

    private static Map<String, String> templateItem(String id, String name, String description, String content) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("description", description);
        m.put("content", content);
        return m;
    }

    private void publishChange(Long productId, String productIdentification, String action) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("productId", productId);
            payload.put("productIdentification", productIdentification);
            payload.put("action", action);
            messageBus.post(SCRIPT_CHANGE_TOPIC, JsonUtils.toJsonString(payload));
        } catch (Exception e) {
            log.warn("[publishChange][脚本变更消息发送失败: {}]", e.getMessage());
        }
    }
}
