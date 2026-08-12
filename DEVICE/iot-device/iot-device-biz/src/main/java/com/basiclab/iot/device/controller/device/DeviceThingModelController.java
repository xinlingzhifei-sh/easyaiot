package com.basiclab.iot.device.controller.device;

import com.basiclab.iot.common.constant.HttpStatus;
import com.basiclab.iot.common.domain.TableDataInfo;
import com.basiclab.iot.common.domain.TableSupport;
import com.basiclab.iot.common.web.controller.BaseController;
import com.basiclab.iot.device.domain.device.vo.TDDeviceDataResp;
import com.basiclab.iot.device.service.device.DeviceThingModelService;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * DeviceThingModelController
 *
 * @author reese
 * @email reese
 */

@Tag(name  = "设备运行状态管理")
@RestController
@RequestMapping("/deviceThingModel")
public class DeviceThingModelController extends BaseController {

    @Resource
    private DeviceThingModelService deviceThingModelService;

    @ApiOperation("获取设备运行状态")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "pageNum", value = "页码", dataType = "int", dataTypeClass = Integer.class, paramType = "query", example = "1"),
            @ApiImplicitParam(name = "pageNo", value = "页码(兼容前端)", dataType = "int", dataTypeClass = Integer.class, paramType = "query", example = "1"),
            @ApiImplicitParam(name = "pageSize", value = "每页显示记录数", dataType = "int", dataTypeClass = Integer.class, paramType = "query", example = "10"),
            @ApiImplicitParam(name = "orderByColumn", value = "排序字段", dataType = "string", dataTypeClass = String.class, paramType = "query"),
            @ApiImplicitParam(name = "isAsc", value = "排序方式（asc/desc）", dataType = "string", dataTypeClass = String.class, paramType = "query")
    })
    @GetMapping(value = "/runtimeStatus")
    // @PreAuthorize("@ss.hasPermission('link:deviceThingModel:query')")
    public TableDataInfo getRuntimeStatus(@RequestParam("id") Long id,
                                          @RequestParam(value = "name", required = false) String name,
                                          @RequestParam(value = "pageNo", required = false) Integer pageNo,
                                          @RequestParam(value = "pageNum", required = false) Integer pageNum,
                                          @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        // 先完整组装运行状态，再内存分页。避免 startPage() 误分页到 selectDeviceById 等前置查询。
        List<TDDeviceDataResp> list = deviceThingModelService.getDeviceThingModels(id, name);
        return buildPagedTable(list, pageNo, pageNum, pageSize);
    }

    private TableDataInfo buildPagedTable(List<TDDeviceDataResp> list, Integer pageNo, Integer pageNum, Integer pageSize) {
        TableDataInfo rspData = new TableDataInfo();
        rspData.setCode(HttpStatus.SUCCESS);
        rspData.setMsg("查询成功");
        if (list == null || list.isEmpty()) {
            rspData.setData(Collections.emptyList());
            rspData.setTotal(0);
            return rspData;
        }

        Integer fallbackSize = TableSupport.buildPageRequest().getPageSize();
        int size = pageSize != null && pageSize > 0
                ? pageSize
                : (fallbackSize != null && fallbackSize > 0 ? fallbackSize : list.size());
        int page = pageNo != null && pageNo > 0 ? pageNo : (pageNum != null && pageNum > 0 ? pageNum : 1);
        int from = Math.min((page - 1) * size, list.size());
        int to = Math.min(from + size, list.size());

        rspData.setData(list.subList(from, to));
        rspData.setTotal(list.size());
        return rspData;
    }

}
