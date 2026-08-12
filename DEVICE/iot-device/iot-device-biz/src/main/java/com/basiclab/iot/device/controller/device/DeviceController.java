package com.basiclab.iot.device.controller.device;

import com.alibaba.fastjson2.JSONObject;
import com.basiclab.iot.common.adapter.ExcelUtil;
import com.basiclab.iot.common.annotation.NoRepeatSubmit;
import com.basiclab.iot.common.domain.AjaxResult;
import com.basiclab.iot.common.domain.R;
import com.basiclab.iot.common.domain.TableDataInfo;
import com.basiclab.iot.common.utils.SnowflakeIdUtil;
import com.basiclab.iot.common.utils.StringUtils;
import com.basiclab.iot.common.web.controller.BaseController;
import com.basiclab.iot.device.constant.DeviceStatusConstant;
import com.basiclab.iot.device.domain.device.oo.DeviceReportOo;
import com.basiclab.iot.device.domain.device.qo.DeviceAlarmQo;
import com.basiclab.iot.device.domain.device.qo.DeviceIsExistQo;
import com.basiclab.iot.device.domain.device.vo.*;
import com.basiclab.iot.device.enums.device.DeviceConnectStatusEnum;
import com.basiclab.iot.device.service.device.DeviceService;
import com.basiclab.iot.device.service.product.ProductService;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotEmpty;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DeviceController
 *
 * @author reese
 * @email reese
 */
@Tag(name = "设备管理")
@RestController
@RequestMapping("/device")
@Slf4j
public class DeviceController extends BaseController {

    static final String EMQX_WEBHOOK_TOKEN_HEADER = "X-Emqx-Webhook-Token";

    @Resource
    private DeviceService deviceService;
    @Resource
    private ProductService productService;
    @Value("${EMQX_WEBHOOK_TOKEN:}")
    private String emqxWebhookToken;

    @PostMapping("/isExist")
    @ApiOperation("判断设备是否存在")
    public R<Boolean> isExist(@RequestBody DeviceIsExistQo deviceIsExistQo) {
        try {
            return R.ok(deviceService.isExist(deviceIsExistQo));
        } catch (Exception e) {
            log.error("判断设备是否存在失败:{}", deviceIsExistQo, e);
            return R.fail("判断设备是否存在失败");
        }
    }

    @PostMapping("/alarm")
    @ApiOperation("边缘网关告警")
    public R<String> alarm(@RequestBody DeviceAlarmQo data) {
        try {
            log.info("收到边缘网关告警, params=" + JSONObject.toJSONString(data));
        } catch (Exception e) {
            log.error("边缘网关告警失败:{}",
                    data, e);
            return R.fail("边缘网关告警失败");
        }
        return R.ok("边缘网关告警成功");
    }

    @PostMapping("/report")
    @ApiOperation("设备上报")
    public R<String> report(@ApiParam("设备上报实体") @Validated @NotEmpty @RequestBody DeviceReportOo deviceReportOo) {
        try {
            log.info("设备上报, params=" + JSONObject.toJSONString(deviceReportOo));
            deviceService.report(deviceReportOo);
        } catch (Exception e) {
            log.error("设备上报数据失败:{}",
                    deviceReportOo, e);
            return R.fail("设备上报当前版本失败");
        }
        return R.ok("设备上报当前版本成功");
    }

    /**
     * emqx的WebHook机制的回调地址
     *
     * @param params
     */
    @ApiOperation("EMQX钩子回调")
    @PostMapping("/webHook")
    @PermitAll
    public void webHook(
            @RequestHeader(value = EMQX_WEBHOOK_TOKEN_HEADER, required = false) String suppliedToken,
            @RequestBody Map<String, Object> params) {
        requireValidEmqxWebhookToken(suppliedToken);
        log.info("EMQX钩子回调, params=" + params);
        String action = (String) params.get("event");
        if (StringUtils.isEmpty(action)) return;
        if (DeviceStatusConstant.CLIENT_CONNECTED.equals(action)) {
            deviceService.handleConnected(params);
        } else if (DeviceStatusConstant.CLIENT_DISCONNECTED.equals(action)) {
            deviceService.handleDisConnected(params);
        } else if (DeviceStatusConstant.SESSION_SUBSCRIBED.equals(action)) {
            deviceService.handleSubscribe(params);
        }
    }

    private void requireValidEmqxWebhookToken(String suppliedToken) {
        if (emqxWebhookToken == null || emqxWebhookToken.length() < 32) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "EMQX_WEBHOOK_TOKEN 未安全配置");
        }
        if (suppliedToken == null || !MessageDigest.isEqual(
                emqxWebhookToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "EMQX WebHook token 无效");
        }
    }


    /**
     * 查询设备管理列表
     */
    @ApiOperation("查询设备列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "pageNum", value = "页码", dataType = "int", dataTypeClass = Integer.class, paramType = "query", example = "1", required = true),
            @ApiImplicitParam(name = "pageSize", value = "每页显示记录数", dataType = "int", dataTypeClass = Integer.class, paramType = "query", example = "10", required = true),
            @ApiImplicitParam(name = "orderByColumn", value = "排序字段", dataType = "string", dataTypeClass = String.class, paramType = "query"),
            @ApiImplicitParam(name = "isAsc", value = "排序方式（asc/desc）", dataType = "string", dataTypeClass = String.class, paramType = "query")
    })
    // @PreAuthorize("@ss.hasPermission('link:device:list')")
    @GetMapping("/list")
    public TableDataInfo list(Device device) {
        startPage();
        List<Device> list = deviceService.selectDeviceList(device);
        return getDataTable(list);
    }

    @ApiOperation("查询设备地图分布点位（供地图展示）")
    @GetMapping("/locations")
    public AjaxResult listDeviceLocations(
            @RequestParam(value = "has_location", required = false, defaultValue = "true") String hasLocation) {
        try {
            boolean hasLocationOnly = !("false".equalsIgnoreCase(hasLocation)
                    || "0".equals(hasLocation)
                    || "no".equalsIgnoreCase(hasLocation));
            List<DeviceMapLocationVO> items = deviceService.listDevicesForMap(hasLocationOnly);
            AjaxResult result = AjaxResult.success(items);
            result.put("total", items.size());
            return result;
        } catch (Exception e) {
            log.error("查询设备地图点位失败", e);
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 通过主产品标识查询产品
     *
     * @param productIdentification 产品标识
     * @return 单条数据
     */
    @GetMapping("/selectByProductIdentification/{productIdentification}")
    public R<?> selectByProductIdentification(@PathVariable(value = "productIdentification") String productIdentification) {
        return R.ok(deviceService.findAllByProductIdentification(productIdentification));
    }

    /**
     * 通过主产品标识查询产品
     *
     * @param productIdentification 产品标识
     * @return 单条数据
     */
    @GetMapping("/selectByProductIdentificationAndDeviceIdentification/{productIdentification}/{deviceIdentification}")
    public R<?> selectByProductIdentificationAndDeviceIdentification(@PathVariable(value = "productIdentification") String productIdentification
            , @PathVariable(value = "deviceIdentification") String deviceIdentification) {
        return R.ok(deviceService.selectByProductIdentificationAndDeviceIdentification(productIdentification, deviceIdentification));
    }

    /**
     * 获取设备列表对应各个状态的设备数量
     *
     * @param device
     * @return
     */
    // @PreAuthorize("@ss.hasPermission('link:device:count')")
    @GetMapping("/listStatusCount")
    public AjaxResult listStatusCount(Device device) {

        Map<String, List<Device>> connectStatusCollect = deviceService.selectDeviceList(device).parallelStream().collect(Collectors.groupingBy(Device::getConnectStatus));

        Map<String, Integer> countMap = new HashMap<>();
        //统计设备在线数量
        countMap.put("onlineCount", !connectStatusCollect.isEmpty() && !CollectionUtils.isEmpty(connectStatusCollect.get(DeviceConnectStatusEnum.ONLINE.getValue())) ? connectStatusCollect.get(DeviceConnectStatusEnum.ONLINE.getValue()).size() : 0);
        //统计设备离线数量
        countMap.put("offlineCount", !connectStatusCollect.isEmpty() && !CollectionUtils.isEmpty(connectStatusCollect.get(DeviceConnectStatusEnum.OFFLINE.getValue())) ? connectStatusCollect.get(DeviceConnectStatusEnum.OFFLINE.getValue()).size() : 0);
        //统计设备初始化数量
        countMap.put("initCount", !connectStatusCollect.isEmpty() && !CollectionUtils.isEmpty(connectStatusCollect.get(DeviceConnectStatusEnum.INIT.getValue())) ? connectStatusCollect.get(DeviceConnectStatusEnum.INIT.getValue()).size() : 0);

        return AjaxResult.success(countMap);
    }

    /**
     * 导出设备管理列表
     */
    // @PreAuthorize("@ss.hasPermission('link:device:export')")

    /// /@Log(title = "设备管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, Device device) throws IOException {
        List<Device> list = deviceService.selectDeviceList(device);
        ExcelUtil<Device> util = new ExcelUtil<Device>(Device.class);
        util.exportExcel(response, list, "设备管理数据");
    }

    @ApiOperation("查询网关下已绑定的子设备")
    @GetMapping("/subDevices")
    public TableDataInfo listSubDevices(@RequestParam("gatewayIdentification") String gatewayIdentification) {
        startPage();
        Device query = new Device();
        query.setParentIdentification(gatewayIdentification);
        query.setDeviceType(Device.deviceTypeEnum.SUBSET.getType());
        List<Device> list = deviceService.selectDeviceList(query);
        return getDataTable(list);
    }

    @ApiOperation("查询可绑定的网关子设备（未关联）")
    @GetMapping("/unboundSubDevices")
    public TableDataInfo listUnboundSubDevices(
            @RequestParam(value = "deviceName", required = false) String deviceName) {
        startPage();
        Device query = new Device();
        query.setDeviceType(Device.deviceTypeEnum.SUBSET.getType());
        query.setIsAssociated(false);
        query.setDeviceName(deviceName);
        List<Device> list = deviceService.selectDeviceList(query);
        return getDataTable(list);
    }

    /**
     * 获取设备管理详细信息
     */
    @ApiOperation("获取设备详细信息")
    // @PreAuthorize("@ss.hasPermission('link:device:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        AjaxResult ajax = AjaxResult.success();
        DeviceParams deviceParams = deviceService.selectDeviceModelById(id);
        DeviceDetail result = new DeviceDetail();
        result.setDevice(deviceParams);
        result.setProduct(productService.selectByProductIdentification(deviceParams.getProductIdentification()));
        ajax.put(AjaxResult.DATA_TAG, result);
        return ajax;
    }

    /**
     * 新增设备管理
     */
    @ApiOperation("添加设备")
    @NoRepeatSubmit
    // @PreAuthorize("@ss.hasPermission('link:device:add')")
    ////@Log(title = "设备管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody Device device) {
        try {
            device.setDeviceIdentification(SnowflakeIdUtil.nextId());
            if (StringUtils.isEmpty(device.getClientId())) {
                device.setClientId("tcp-" + device.getDeviceIdentification());
            }
            if (StringUtils.isEmpty(device.getDeviceStatus())) {
                device.setDeviceStatus("ENABLE");
            }
            if (StringUtils.isEmpty(device.getConnectStatus())) {
                device.setConnectStatus("OFFLINE");
            }
            return toAjax(deviceService.insertDevice(device));
        } catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 修改设备管理
     */
    @ApiOperation("编辑设备信息")
    @NoRepeatSubmit
    // @PreAuthorize("@ss.hasPermission('link:device:edit')")
    ////@Log(title = "设备管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody Device device) {
        try {
            return toAjax(deviceService.updateDevice(device));
        } catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 查询设备扩展信息
     */
    @ApiOperation("查询设备扩展信息")
    @PostMapping("/extension/query")
    public R<DeviceExtensionDataVO> queryDeviceExtensionData(@Validated @RequestBody DeviceExtensionQueryRequest request) {
        try {
            DeviceExtensionDataVO result = deviceService.queryDeviceExtensionData(request);
            if (result == null) {
                return R.fail("设备不存在或扩展信息为空");
            }
            return R.ok(result);
        } catch (Exception e) {
            log.error("查询设备扩展信息失败：{}", e.getMessage(), e);
            return R.fail("查询设备扩展信息失败：" + e.getMessage());
        }
    }

    /**
     * 删除设备管理
     */
    @ApiOperation("删除设备")
    // @PreAuthorize("@ss.hasPermission('link:device:remove')")
    ////@Log(title = "设备管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(deviceService.deleteDeviceByIds(ids));
    }

    /**
     * 更新设备在线状态
     */
    @PutMapping("/updateConnectStatusByClientId")
    public R updateConnectStatusByClientId(@RequestBody Device device) {
        return R.ok(deviceService.updateConnectStatusByClientId(device.getConnectStatus(), device.getClientId()));
    }

    /**
     * 校验clientId是否存在
     *
     * @param clientId
     * @return
     */
    /// /@Log(title = "设备管理", businessType = BusinessType.OTHER)
    @GetMapping(value = "/validationFindOneByClientId/{clientId}")
    public AjaxResult validationFindOneByClientId(@PathVariable("clientId") String clientId) {
        if (StringUtils.isEmpty(clientId)) {
            return AjaxResult.error("clientId不可为空");
        }
        Device findOneByClientId = deviceService.findOneByClientId(clientId);
        if (StringUtils.isNull(findOneByClientId)) {
            return AjaxResult.success("clientId可用");
        }
        return AjaxResult.error("clientId已存在");
    }

    /**
     * 校验设备标识是否存在
     *
     * @param deviceIdentification
     * @return
     */
    /// /@Log(title = "设备管理", businessType = BusinessType.OTHER)
    @GetMapping(value = "/validationFindOneByDeviceIdentification/{deviceIdentification}")
    public AjaxResult validationFindOneByDeviceIdentification(@PathVariable("deviceIdentification") String deviceIdentification) {
        if (StringUtils.isEmpty(deviceIdentification)) {
            return AjaxResult.error("设备标识不可为空");
        }
        Device findOneByDeviceIdentification = deviceService.findOneByDeviceIdentification(deviceIdentification);
        if (StringUtils.isNull(findOneByDeviceIdentification)) {
            return AjaxResult.success("设备标识可用");
        }
        return AjaxResult.error("设备标识已存在");
    }


    /**
     * 设备断开连接接口
     */
    // @PreAuthorize("@ss.hasPermission('link:device:disconnect')")

    /// /@Log(title = "设备管理", businessType = BusinessType.OTHER)
    @PostMapping("/disconnect/{ids}")
    public AjaxResult disconnect(@PathVariable Long[] ids) {
        final Boolean disconnect = deviceService.disconnect(ids);
        return disconnect ? AjaxResult.success("操作成功") : AjaxResult.error("操作失败");
    }

    /**
     * smqttx客户端身份认证
     *
     * @param params
     * @return
     */
    @Deprecated
    @PostMapping("/clientAuthentication")
    public ResponseEntity<AjaxResult> clientAuthentication(@RequestBody Map<String, Object> params) {
        final Object clientIdentifier = params.get("clientIdentifier");
        final Object username = params.get("username");
        final Object password = params.get("password");
        final Object deviceStatus = params.get("deviceStatus");
        final Object protocolType = params.get("protocolType");

        Device device = deviceService.clientAuthentication(clientIdentifier.toString(), username.toString(), password.toString(), deviceStatus.toString(), protocolType.toString());
        log.info("{} 协议设备正在进行身份认证,客户端ID:{},用户名:{},密码:{},认证结果:{}", protocolType, clientIdentifier, username, password, device != null ? "成功" : "失败");

        return device != null ? ResponseEntity.ok().body(AjaxResult.success("认证成功")) : ResponseEntity.status(403).body(AjaxResult.error("认证失败"));
    }

    /**
     * 根据客户端标识获取设备信息
     *
     * @param clientId
     * @return
     */
    @PostMapping("/findOneByClientId")
    public R<Device> findOneByClientId(@RequestBody String clientId) {
        return R.ok(deviceService.findOneByClientId(clientId));
    }


    /**
     * 根据产品标识获取产品所有关联设备
     *
     * @param productIdentification
     * @return
     */
    @GetMapping("/selectAllByProductIdentification/{productIdentification}")
    public R<List<Device>> selectAllByProductIdentification(@PathVariable(value = "productIdentification") String productIdentification) {
        return R.ok(deviceService.findAllByProductIdentification(productIdentification));
    }


    @PostMapping("/selectDeviceByDeviceIdentificationList")
    public R<?> selectDeviceByDeviceIdentificationList(@RequestBody List<String> deviceIdentificationList) {
        return R.ok(deviceService.selectDeviceByDeviceIdentificationList(deviceIdentificationList));
    }

    @ApiOperation("关联网关子设备")
    @PostMapping("/associateGateway")
    public AjaxResult associateGateway(@RequestBody AssociateGatewayRequest associateGatewayRequest) {
        try {
            int successCount = deviceService.associateGateway(associateGatewayRequest.getIdList(), associateGatewayRequest.getTargetDeviceIdentification());
            if (successCount == 0) {
                return AjaxResult.error("关联失败");
            }
            return AjaxResult.success("成功关联 " + successCount + " 个子设备");
        } catch (Exception e) {
            log.error("关联网关子设备失败", e);
            return AjaxResult.error(e.getMessage());
        }
    }

    @ApiOperation("解绑网关子设备")
    @PostMapping("/disassociateGateway")
    public AjaxResult disassociateGateway(@RequestBody List<Long> idList) {
        try {
            int successCount = deviceService.disassociateGateway(idList);
            if (successCount == 0) {
                return AjaxResult.error("解绑失败");
            }
            return AjaxResult.success("成功解绑 " + successCount + " 个子设备");
        } catch (Exception e) {
            log.error("解绑网关子设备失败", e);
            return AjaxResult.error(e.getMessage());
        }
    }

    @ApiOperation("网关代报时确保子设备存在（自动创建并绑定）")
    @PostMapping("/ensureGatewaySubDevice")
    public R<Device> ensureGatewaySubDevice(@RequestBody EnsureGatewaySubDeviceParam param) {
        try {
            return R.ok(deviceService.ensureGatewaySubDevice(param));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        } catch (Exception e) {
            log.error("确保网关子设备失败", e);
            return R.fail(e.getMessage());
        }
    }

    @ApiOperation("网关删除子设备拓扑关系")
    @PostMapping("/detachGatewaySubDevices")
    public R<Integer> detachGatewaySubDevices(@RequestParam("gatewayIdentification") String gatewayIdentification,
                                              @RequestBody List<String> subDeviceIdentifications) {
        try {
            return R.ok(deviceService.detachGatewaySubDevices(gatewayIdentification, subDeviceIdentifications));
        } catch (Exception e) {
            log.error("删除网关子设备拓扑失败", e);
            return R.fail(e.getMessage());
        }
    }

    @ApiOperation("网关上报子设备在线状态")
    @PostMapping("/updateGatewaySubDeviceStatus")
    public R<Integer> updateGatewaySubDeviceStatus(@RequestParam("gatewayIdentification") String gatewayIdentification,
                                                   @RequestBody List<Map<String, Object>> statusItems) {
        try {
            return R.ok(deviceService.updateGatewaySubDeviceStatus(gatewayIdentification, statusItems));
        } catch (Exception e) {
            log.error("更新网关子设备状态失败", e);
            return R.fail(e.getMessage());
        }
    }

    @ApiOperation("上行时确保设备存在（GATEWAY/COMMON 自动建档）")
    @PostMapping("/ensureDeviceOnUplink")
    public R<Device> ensureDeviceOnUplink(@RequestBody EnsureDeviceOnUplinkParam param) {
        try {
            return R.ok(deviceService.ensureDeviceOnUplink(param));
        } catch (IllegalArgumentException e) {
            return R.fail(e.getMessage());
        } catch (Exception e) {
            log.error("上行自动创建设备失败", e);
            return R.fail(e.getMessage());
        }
    }

    @ApiOperation("调用设备服务（MQTT 下行）")
    @PostMapping("/{deviceId}/invokeService")
    public AjaxResult invokeService(@PathVariable("deviceId") Long deviceId,
                                    @RequestParam String serviceIdentifier,
                                    @RequestBody(required = false) Object params) {
        try {
            Map<String, Object> result = deviceService.invokeService(deviceId, serviceIdentifier, params);
            return result != null ? AjaxResult.success("服务调用已下发", result)
                    : AjaxResult.error("服务调用失败");
        } catch (IllegalArgumentException ex) {
            return AjaxResult.error(ex.getMessage());
        }
    }

    @ApiOperation("下发设备属性期望值（MQTT 下行 thing.property.set）")
    @PostMapping("/{deviceId}/setProperties")
    public AjaxResult setProperties(@PathVariable("deviceId") Long deviceId,
                                    @RequestBody Object params) {
        try {
            Map<String, Object> result = deviceService.setProperties(deviceId, params);
            return result != null ? AjaxResult.success("属性期望值已下发", result)
                    : AjaxResult.error("属性下发失败");
        } catch (IllegalArgumentException ex) {
            return AjaxResult.error(ex.getMessage());
        }
    }

    @ApiOperation("获取设备连接状态统计")
    @GetMapping("/getConnectStatusStatistics")
    public AjaxResult getConnectStatusStatistics() {
        ConnectStatusStatisticsVo connectStatusStatisticsVo = deviceService.getConnectStatusStatistics();
        return AjaxResult.success(connectStatusStatisticsVo);
    }

    @ApiOperation("获取设备统计")
    @GetMapping("/getDeviceStatistics")
    public AjaxResult getDeviceStatistics() {
        DeviceStatisticsVo connectStatusStatisticsVo = deviceService.getDeviceStatistics();
        return AjaxResult.success(connectStatusStatisticsVo);
    }

    @ApiOperation("获取设备激活状态统计")
    @GetMapping("/getDeviceStatusStatistics")
    public AjaxResult getDeviceStatusStatistics() {
        DeviceStatusStatisticsVo connectStatusStatisticsVo = deviceService.getDeviceStatusStatistics();
        return AjaxResult.success(connectStatusStatisticsVo);
    }

    @ApiOperation("查询 IoT 设备已关联的流媒体摄像头")
    @GetMapping("/cameraLinks")
    public TableDataInfo listCameraLinks(@RequestParam("iotDeviceId") Long iotDeviceId) {
        startPage();
        List<DeviceCameraLink> list = deviceService.listDeviceCameraLinks(iotDeviceId);
        return getDataTable(list);
    }

    @ApiOperation("查询已被绑定的流媒体摄像头 ID")
    @GetMapping("/boundCameraIds")
    public AjaxResult listBoundCameraIds() {
        return AjaxResult.success(deviceService.listBoundCameraIds());
    }

    @ApiOperation("关联流媒体摄像头")
    @PostMapping("/associateCameras")
    public AjaxResult associateCameras(@RequestBody AssociateCamerasRequest request) {
        try {
            int successCount = deviceService.associateCameras(
                    request.getIotDeviceId(), request.getCameraDeviceIds());
            if (successCount == 0) {
                return AjaxResult.error("关联失败");
            }
            return AjaxResult.success("成功关联 " + successCount + " 个摄像头");
        } catch (Exception e) {
            log.error("关联流媒体摄像头失败", e);
            return AjaxResult.error(e.getMessage());
        }
    }

    @ApiOperation("解绑流媒体摄像头")
    @PostMapping("/disassociateCameras")
    public AjaxResult disassociateCameras(@RequestBody List<Long> linkIds) {
        try {
            int successCount = deviceService.disassociateCameras(linkIds);
            if (successCount == 0) {
                return AjaxResult.error("解绑失败");
            }
            return AjaxResult.success("成功解绑 " + successCount + " 个摄像头");
        } catch (Exception e) {
            log.error("解绑流媒体摄像头失败", e);
            return AjaxResult.error(e.getMessage());
        }
    }

    @ApiOperation("查询单个设备地图位置")
    @GetMapping("/{id}/location")
    public AjaxResult getDeviceLocation(@PathVariable("id") Long id) {
        try {
            return AjaxResult.success(deviceService.getDeviceMapLocation(id));
        } catch (Exception e) {
            log.error("查询设备地图位置失败, id={}", id, e);
            return AjaxResult.error(e.getMessage());
        }
    }

    @ApiOperation("保存或更新设备地图坐标")
    @PutMapping("/{id}/location")
    public AjaxResult updateDeviceLocation(
            @PathVariable("id") Long id,
            @RequestBody DeviceLocationUpdateParam param) {
        try {
            int rows = deviceService.saveOrUpdateDeviceMapLocation(id, param);
            return rows > 0 ? AjaxResult.success("保存成功") : AjaxResult.error("保存失败");
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("保存设备地图坐标失败, id={}", id, e);
            return AjaxResult.error(e.getMessage());
        }
    }

}
