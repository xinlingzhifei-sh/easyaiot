package com.basiclab.iot.tdengine.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.basiclab.iot.device.constant.FunctionTypeConstant;
import com.basiclab.iot.device.constant.TdengineConstant;
import com.basiclab.iot.device.domain.device.vo.TDDeviceDataResp;
import com.basiclab.iot.tdengine.constant.SuperTableTypeConstant;
import com.basiclab.iot.tdengine.domain.DeviceData;
import com.basiclab.iot.tdengine.domain.Fields;
import com.basiclab.iot.tdengine.domain.SelectDto;
import com.basiclab.iot.tdengine.domain.SuperTableDescribeVO;
import com.basiclab.iot.tdengine.domain.model.SuperTableDTO;
import com.basiclab.iot.tdengine.domain.model.TableDTO;
import com.basiclab.iot.tdengine.domain.model.TagsSelectDTO;
import com.basiclab.iot.tdengine.domain.query.TDDeviceDataHistoryRequest;
import com.basiclab.iot.tdengine.domain.query.TDDeviceDataRequest;
import com.basiclab.iot.tdengine.mapper.TdEngineMapper;
import com.basiclab.iot.tdengine.service.TdEngineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author reese
 * @email reese
 */
@Service
@Slf4j
public class TdEngineServiceImpl implements TdEngineService {

    @Resource
    private TdEngineMapper tdengineMapper;

    @Override
    public void createDatabase(String dataBaseName) {
        tdengineMapper.createDatabase(dataBaseName);
    }

    @Override
    public void createSuperTable(String dataBaseName, String superTableName) {
        tdengineMapper.createSuperTable(dataBaseName, superTableName);
    }

    @Override
    public void createSuperTableAndColumn(SuperTableDTO superTableDTO) {
        tdengineMapper.createSuperTableAndColumn(superTableDTO);
    }

    @Override
    public void createSubTable(TableDTO tableDTO) {
        tdengineMapper.createSubTable(tableDTO);
    }

    @Override
    public void dropSuperTable(String dataBaseName, String superTableName) {
        tdengineMapper.dropSuperTable(dataBaseName, superTableName);
    }

    @Override
    public void alterSuperTableColumn(String dataBaseName, String superTableName, Fields fields) {
        tdengineMapper.alterSuperTableColumn(dataBaseName, superTableName, fields);
    }

    @Override
    public void dropSuperTableColumn(String dataBaseName, String superTableName, Fields fields) {
        tdengineMapper.dropSuperTableColumn(dataBaseName, superTableName, fields);
    }

    @Override
    public List<SuperTableDescribeVO> describeSuperOrSubTable(String dataBaseName, String tableName) {
        try {
            return tdengineMapper.describeSuperOrSubTable(dataBaseName, tableName);
        } catch (Exception e) {
            log.warn("Error describing super or sub table. Database: {}, Table: {}, Error: {}", dataBaseName, tableName, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void alterSuperTableTag(String dataBaseName, String superTableName, Fields fields) {
        tdengineMapper.alterSuperTableTag(dataBaseName, superTableName, fields);
    }

    @Override
    public void dropSuperTableTag(String dataBaseName, String superTableName, Fields fields) {
        tdengineMapper.dropSuperTableTag(dataBaseName, superTableName, fields);
    }

    @Override
    public void alterSuperTableTagRename(String dataBaseName, String superTableName, String oldName, String newName) {
        tdengineMapper.alterSuperTableTagRename(dataBaseName, superTableName, oldName, newName);
    }

    @Override
    public void insertTableData(TableDTO tableDTO) {
        tdengineMapper.insertTableData(tableDTO);
    }

    @Override
    public Map<String, Map<String, Object>> getLastDataByTags(TagsSelectDTO tagsSelectDTO) {
        List<Map<String, Object>> maps = tdengineMapper.getLastDataByTags(tagsSelectDTO);
        Map<String, Map<String, Object>> objectHashMap = new HashMap<>();

        for (Map<String, Object> map : maps) {
            Optional.ofNullable(map.get(tagsSelectDTO.getTagsName()))
                    .map(Object::toString)
                    .ifPresent(key -> objectHashMap.put(key, map));
        }
        return objectHashMap;
    }


    @Override
    public List<Map<String, Object>> getDataInRangeOrLastRecord(String dataBaseName, String tableName, Long startTime, Long endTime) {
        return tdengineMapper.getDataInRangeOrLastRecord(dataBaseName, tableName, startTime, endTime);
    }


    @Override
    public List<Map<String, Object>> selectByTimesTamp(SelectDto selectDto) throws Exception {
        List<Map<String, Object>> maps = tdengineMapper.selectByTimestamp(selectDto);
        for (Map<String, Object> map : maps) {
            Map<String, Object> filterMap = map.entrySet()
                    .stream()
                    .filter(entry -> entry.getValue() != null)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        return maps;
    }


    /**
     * @param selectDto
     * @return
     */
    @Override
    public List<Map<String, Object>> getLastData(SelectDto selectDto) throws Exception {
        List<Map<String, Object>> maps = this.tdengineMapper.getLastData(selectDto);
//        for (Map<String, Object> map : maps) {
//            Map<String, Object> filterMap = map.entrySet()
//                    .stream()
//                    .filter(entry -> entry.getValue() != null)
//                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
//        }
        return maps;
    }

    @Override
    public List<DeviceData> getLastRowsListByIdentifier(TDDeviceDataRequest tdDeviceDataRequest) {
        List<DeviceData> list = new ArrayList<>();
        try {
            // deviceIdentification 用作超级表 TAG，保持原始设备标识（勿做子表名清洗）
            if (tdDeviceDataRequest.getTdSuperTableName() == null
                    || "device_data".equals(tdDeviceDataRequest.getTdSuperTableName())) {
                tdDeviceDataRequest.setTdSuperTableName(SuperTableTypeConstant.PROPERTY_UPSTREAM_REPORT);
            }
            list = tdengineMapper.getLastRowsListByIdentifier(tdDeviceDataRequest);
        } catch (Exception e) {
            log.error("getLastRowsListByIdentifier error: {}", e.getMessage());
        }
        return list;
    }

    @Override
    public List<TDDeviceDataResp> deviceInfoHistoryPage(TDDeviceDataHistoryRequest request) {
        request.setFunctionType(FunctionTypeConstant.PROPERTIES);
        request.setTdDatabaseName(TdengineConstant.IOT_DEVICE);
        // 按超级表 TAG(device_identification) 查询，与 sink 写入 TAG 一致
        request.setTdSuperTableName(SuperTableTypeConstant.PROPERTY_UPSTREAM_REPORT);
        try {
            List<TDDeviceDataResp> rows = tdengineMapper.getDeviceHistory(request);
            return extractHistoryPropertyValues(rows, request.getIdentifier());
        } catch (Exception e) {
            log.error("查询运行时属性异常：" + e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * 历史表存的是整包 params JSON，按 identifier 提取单属性值与工业协议原始报文。
     */
    private List<TDDeviceDataResp> extractHistoryPropertyValues(List<TDDeviceDataResp> rows, String identifier) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        String propertyCode = StrUtil.trimToEmpty(identifier);
        List<TDDeviceDataResp> result = new ArrayList<>(rows.size());
        for (TDDeviceDataResp row : rows) {
            if (row == null) {
                continue;
            }
            String raw = row.getDataValue();
            if (StrUtil.isBlank(raw)) {
                continue;
            }
            String text = raw.trim();
            if (!text.startsWith("{") && !text.startsWith("[")) {
                if (StrUtil.isNotBlank(propertyCode)) {
                    row.setPropertyCode(propertyCode);
                }
                result.add(row);
                continue;
            }
            JSONObject params;
            try {
                params = JSONUtil.parseObj(text);
            } catch (Exception e) {
                // 非标准 JSON 时保留原值，避免历史完全不可见
                if (StrUtil.isNotBlank(propertyCode)) {
                    row.setPropertyCode(propertyCode);
                }
                result.add(row);
                continue;
            }
            Object value = resolveHistoryValue(params, propertyCode);
            if (value == null && StrUtil.isNotBlank(propertyCode)) {
                // 该上报包不含目标属性，跳过
                continue;
            }
            if (value != null) {
                row.setDataValue(String.valueOf(value));
            }
            if (StrUtil.isNotBlank(propertyCode)) {
                row.setPropertyCode(propertyCode);
                Object rawPayload = resolveHistoryRaw(params, propertyCode);
                if (rawPayload != null) {
                    row.setRawData(String.valueOf(rawPayload));
                }
            }
            result.add(row);
        }
        return result;
    }

    private Object resolveHistoryValue(JSONObject params, String propertyCode) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        if (StrUtil.isNotBlank(propertyCode)) {
            if (params.containsKey(propertyCode)) {
                return params.get(propertyCode);
            }
            Object nested = params.get("properties");
            if (nested instanceof JSONObject && ((JSONObject) nested).containsKey(propertyCode)) {
                return ((JSONObject) nested).get(propertyCode);
            }
            nested = params.get("data");
            if (nested instanceof JSONObject && ((JSONObject) nested).containsKey(propertyCode)) {
                return ((JSONObject) nested).get(propertyCode);
            }
            if (params.get("_value") != null) {
                return params.get("_value");
            }
            return null;
        }
        if (params.get("_value") != null) {
            return params.get("_value");
        }
        List<Map.Entry<String, Object>> candidates = params.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().startsWith("_"))
                .collect(Collectors.toList());
        if (candidates.size() == 1) {
            return candidates.get(0).getValue();
        }
        return null;
    }

    private Object resolveHistoryRaw(JSONObject params, String propertyCode) {
        if (params == null || StrUtil.isBlank(propertyCode)) {
            return null;
        }
        Object rawObj = params.get("_raw");
        if (rawObj instanceof JSONObject) {
            return ((JSONObject) rawObj).get(propertyCode);
        }
        if (rawObj instanceof Map) {
            return ((Map<?, ?>) rawObj).get(propertyCode);
        }
        return null;
    }

    @Override
    public List<Map<String, Object>> queryDeviceTimeSeriesData(com.basiclab.iot.tdengine.domain.query.DeviceTimeSeriesQueryRequest request) {
        try {
            if (request.getTdDatabaseName() == null) {
                request.setTdDatabaseName("iot_device");
            }
            if (request.getSuperTableName() == null) {
                String type = request.getSuperTableType();
                if (type != null && !type.startsWith("st_")) {
                    // 允许传枚举名 PROPERTY_UPSTREAM_REPORT
                    request.setSuperTableName("st_" + type.toLowerCase());
                } else {
                    request.setSuperTableName(type);
                }
            }
            // deviceIdentification 为超级表 TAG，保持原始值
            return tdengineMapper.queryDeviceTimeSeriesData(request);
        } catch (Exception e) {
            log.error("查询设备时序数据异常：deviceIdentification={}, superTableType={}, error={}",
                    request.getDeviceIdentification(), request.getSuperTableType(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }

}
