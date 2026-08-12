package com.basiclab.iot.device.controller.device;

import com.basiclab.iot.common.domain.AjaxResult;
import com.basiclab.iot.common.domain.TableDataInfo;
import com.basiclab.iot.common.utils.StringUtils;
import com.basiclab.iot.common.web.controller.BaseController;
import com.basiclab.iot.device.domain.device.vo.Device;
import com.basiclab.iot.device.domain.device.vo.DeviceEvent;
import com.basiclab.iot.device.service.device.DeviceEventService;
import com.basiclab.iot.device.service.device.DeviceService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import org.springframework.format.annotation.DateTimeFormat;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DeviceEventController
 *
 * @author reese
 * @email reese
 */
@Api(tags = "设备事件管理")
@RestController
@RequestMapping("/deviceEvent")
public class DeviceEventController extends BaseController {

    @Resource
    private DeviceEventService deviceEventService;

    @Resource
    private DeviceService deviceService;

    /**
     * 查询设备事件列表
     * 兼容前端传 deviceId：自动转换为 deviceIdentification
     */
    @ApiOperation("查询设备事件列表")
    @GetMapping("/list")
    public TableDataInfo list(DeviceEvent deviceEvent,
                              @RequestParam(required = false) Long deviceId,
                              @RequestParam(required = false)
                              @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                              @RequestParam(required = false)
                              @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        if (StringUtils.isEmpty(deviceEvent.getDeviceIdentification()) && deviceId != null) {
            Device device = deviceService.findOneById(deviceId);
            if (device != null) {
                deviceEvent.setDeviceIdentification(device.getDeviceIdentification());
            }
        }
        if (startTime != null) {
            deviceEvent.setStartTime(startTime);
        }
        if (endTime != null) {
            deviceEvent.setEndTime(endTime);
        }
        startPage();
        List<DeviceEvent> list = deviceEventService.selectDeviceEventList(deviceEvent);
        return getDataTable(list);
    }

    @ApiOperation("获取设备事件详细信息")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return AjaxResult.success(deviceEventService.selectDeviceEventById(id));
    }

    @ApiOperation("新增设备事件")
    @PostMapping
    public AjaxResult add(@RequestBody DeviceEvent deviceEvent) {
        return toAjax(deviceEventService.insertDeviceEvent(deviceEvent));
    }

    @ApiOperation("修改设备事件")
    @PutMapping
    public AjaxResult edit(@RequestBody DeviceEvent deviceEvent) {
        return toAjax(deviceEventService.updateDeviceEvent(deviceEvent));
    }

    @ApiOperation("删除设备事件")
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable String[] ids) {
        return toAjax(deviceEventService.deleteDeviceEventByIds(ids));
    }
}
