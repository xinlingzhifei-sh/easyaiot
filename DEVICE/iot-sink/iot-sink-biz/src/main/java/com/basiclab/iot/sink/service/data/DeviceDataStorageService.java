package com.basiclab.iot.sink.service.data;

import com.basiclab.iot.common.core.util.TenantUtils;
import com.basiclab.iot.sink.dal.mapper.DeviceMapper;
import com.basiclab.iot.device.enums.device.DataTypeEnum;
import com.basiclab.iot.sink.enums.IotDeviceTopicEnum;
import com.basiclab.iot.sink.enums.IotDeviceTopicMethodMapping;
import com.basiclab.iot.sink.mq.message.IotDeviceMessage;
import com.basiclab.iot.sink.service.tdengine.TdEngineService;
import com.basiclab.iot.tdengine.domain.Fields;
import com.basiclab.iot.tdengine.domain.model.TableDTO;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeviceDataStorageService
 *
 * @author reese
 * @email reese
 */

@Slf4j
@Service
public class DeviceDataStorageService {

    /**
     * TDEngine数据库名称
     */
    private static final String TD_DATABASE_NAME = "iot_device";

    @Resource
    private TdEngineService tdEngineService;

    @Resource
    private DeviceRedisStorageService deviceRedisStorageService;

    @Resource
    private DeviceMapper deviceMapper;

    /**
     * 存储设备消息数据
     * <p>
     * 同时将数据存储到TDEngine（历史数据）和Redis（设备数据缓存）
     * <p>
     * 在入库前会根据 Topic 标准映射验证并标准化 method 字段
     *
     * @param message   设备消息
     * @param topicEnum Topic枚举
     */
    public void storeDeviceData(IotDeviceMessage message, IotDeviceTopicEnum topicEnum) {
        if (message == null || topicEnum == null) {
            log.warn("[storeDeviceData][消息或Topic枚举为空，跳过存储]");
            return;
        }

        // 0. 根据 Topic 标准映射验证并标准化 method 字段
        normalizeMethodByTopic(message, topicEnum);

        // 页面直接读取 PostgreSQL，优先保证设备状态、影子和日志可用。
        storeToDevice(message, topicEnum);

        try {
            // 1. 存储到TDEngine（历史数据）
            storeToTdEngine(message, topicEnum);
        } catch (Exception e) {
            log.error("[storeDeviceData][TDEngine存储失败，messageId: {}, topic: {}]",
                    message.getId(), topicEnum.name(), e);
        }

        try {
            // 2. 存储到Redis（设备数据缓存）
            deviceRedisStorageService.storeDeviceData(message, topicEnum);
        } catch (Exception e) {
            log.error("[storeDeviceData][Redis存储失败，messageId: {}, topic: {}]",
                    message.getId(), topicEnum.name(), e);
        }

        log.debug("[storeDeviceData][数据存储处理完成，messageId: {}, topic: {}, method: {}]",
                message.getId(), topicEnum.name(), message.getMethod());
    }

    /**
     * 根据 Topic 标准映射验证并标准化 method 字段
     * <p>
     * 如果消息中的 method 与 Topic 标准映射不一致，会使用标准映射中的 method
     * 如果消息中没有 method，会根据 Topic 标准映射自动设置
     *
     * @param message   设备消息
     * @param topicEnum Topic枚举
     */
    private void storeToDevice(IotDeviceMessage message, IotDeviceTopicEnum topicEnum) {
        if (message.getTenantId() == null || StrUtil.isBlank(message.getDeviceId())) {
            log.warn("[storeToDevice][tenantId or deviceId is missing, messageId: {}]", message.getId());
            return;
        }
        try {
            Long deviceId = Long.valueOf(message.getDeviceId());
            TenantUtils.execute(message.getTenantId(), () -> {
                LocalDateTime reportTime = message.getReportTime() != null
                        ? message.getReportTime() : LocalDateTime.now();
                int updated = deviceMapper.updateDeviceConnectStatus(deviceId, "ONLINE", reportTime);
                if (updated == 0) {
                    log.warn("[storeToDevice][device was not updated, messageId: {}, tenantId: {}, deviceId: {}]",
                            message.getId(), message.getTenantId(), deviceId);
                    return;
                }

                if (topicEnum == IotDeviceTopicEnum.SHADOW_UPSTREAM_REPORT
                        || topicEnum == IotDeviceTopicEnum.PROPERTY_UPSTREAM_REPORT) {
                    if (message.getParams() != null) {
                        deviceMapper.updateDeviceShadow(deviceId, JSONUtil.toJsonStr(message.getParams()));
                    }
                } else if (topicEnum == IotDeviceTopicEnum.LOG_UPSTREAM_REPORT) {
                    Map<String, Object> logEntry = new LinkedHashMap<>();
                    logEntry.put("id", message.getId());
                    logEntry.put("actionType", "REPORT");
                    logEntry.put("userName", extractDeviceIdentification(message));
                    logEntry.put("status", "SUCCESS");
                    logEntry.put("actionData", message.getParams());
                    logEntry.put("level", extractLogField(message.getParams(), "level", "INFO"));
                    logEntry.put("content", extractLogField(message.getParams(), "content",
                            extractLogField(message.getParams(), "message",
                                    extractLogField(message.getParams(), "log", null))));
                    logEntry.put("createTime", reportTime.toString());
                    int logUpdated = deviceMapper.appendDeviceLog(
                            deviceId, message.getTenantId(), JSONUtil.toJsonStr(logEntry));
                    if (logUpdated == 0) {
                        log.warn("[storeToDevice][device log was not appended, messageId: {}, deviceId: {}]",
                                message.getId(), deviceId);
                    }
                }
            });
        } catch (Exception e) {
            log.error("[storeToDevice][failed to persist device state, messageId: {}, topic: {}]",
                    message.getId(), topicEnum.name(), e);
        }
    }

    private void normalizeMethodByTopic(IotDeviceMessage message, IotDeviceTopicEnum topicEnum) {
        // 获取 Topic 对应的标准 Method
        String standardMethod = IotDeviceTopicMethodMapping.getMethodByTopic(topicEnum);
        
        if (StrUtil.isBlank(standardMethod)) {
            // 如果该 Topic 没有标准 Method 映射，保持原有 method 不变
            if (StrUtil.isBlank(message.getMethod())) {
                log.debug("[normalizeMethodByTopic][Topic {} 没有标准 Method 映射，且消息中 method 为空，保持为空]", 
                        topicEnum.name());
            }
            return;
        }

        // 如果消息中的 method 为空，使用标准 Method
        if (StrUtil.isBlank(message.getMethod())) {
            message.setMethod(standardMethod);
            log.debug("[normalizeMethodByTopic][消息 method 为空，根据 Topic {} 标准映射设置为: {}]", 
                    topicEnum.name(), standardMethod);
            return;
        }

        // 如果消息中的 method 与标准 Method 不一致，记录警告并使用标准 Method
        if (!standardMethod.equals(message.getMethod())) {
            log.warn("[normalizeMethodByTopic][消息 method ({}) 与 Topic {} 标准映射 ({}) 不一致，使用标准 Method]", 
                    message.getMethod(), topicEnum.name(), standardMethod);
            message.setMethod(standardMethod);
        } else {
            log.debug("[normalizeMethodByTopic][消息 method ({}) 与 Topic {} 标准映射一致]", 
                    message.getMethod(), topicEnum.name());
        }
    }

    /**
     * 存储数据到TDEngine
     *
     * @param message   设备消息
     * @param topicEnum Topic枚举
     */
    private void storeToTdEngine(IotDeviceMessage message, IotDeviceTopicEnum topicEnum) {
        try {
            // 属性上报若无 params，写入空行会把「最新一条」冲掉，导致运行状态间歇性无值
            if (topicEnum == IotDeviceTopicEnum.PROPERTY_UPSTREAM_REPORT && message.getParams() == null) {
                log.warn("[storeToTdEngine][属性上报 params 为空，跳过 TDEngine 写入，messageId: {}]", message.getId());
                return;
            }

            // 获取超级表名称
            String superTableName = getSuperTableName(topicEnum);
            if (StrUtil.isBlank(superTableName)) {
                log.warn("[storeToTdEngine][未找到对应的超级表，topic: {}]", topicEnum.name());
                return;
            }

            // 构建子表名称（与查询侧约定：超级表_设备标识）
            String deviceIdentification = extractDeviceIdentification(message);
            String tableName = buildTableName(superTableName,
                    StrUtil.blankToDefault(deviceIdentification, message.getDeviceId()));

            // 构建字段值列表
            List<Fields> schemaFieldValues = buildSchemaFieldValues(message, topicEnum);
            List<Fields> tagsFieldValues = buildTagsFieldValues(message, topicEnum);

            // 构建TableDTO
            TableDTO tableDTO = new TableDTO();
            tableDTO.setDataBaseName(TD_DATABASE_NAME);
            tableDTO.setSuperTableName(superTableName);
            tableDTO.setTableName(tableName);
            tableDTO.setSchemaFieldValues(schemaFieldValues);
            tableDTO.setTagsFieldValues(tagsFieldValues);

            // 调用TDEngine服务插入数据
            tdEngineService.insertTableData(tableDTO);

            log.debug("[storeToTdEngine][TDEngine数据插入成功，tableName: {}]", tableName);
        } catch (Exception e) {
            log.error("[storeToTdEngine][TDEngine数据插入失败，messageId: {}, topic: {}]", message.getId(), topicEnum.name(), e);
        }
    }

    /**
     * 获取超级表名称
     *
     * @param topicEnum Topic枚举
     * @return 超级表名称
     */
    private String getSuperTableName(IotDeviceTopicEnum topicEnum) {
        switch (topicEnum) {
            case PROPERTY_UPSTREAM_REPORT:
                return "st_property_upstream_report";
            case PROPERTY_UPSTREAM_DESIRED_SET_ACK:
                return "st_property_upstream_desired_set_ack";
            case PROPERTY_UPSTREAM_DESIRED_QUERY_RESPONSE:
                return "st_property_upstream_desired_query_response";
            case EVENT_UPSTREAM_REPORT:
                return "st_event_upstream_report";
            case SERVICE_UPSTREAM_INVOKE_RESPONSE:
                return "st_service_upstream_invoke_response";
            case DEVICE_TAG_UPSTREAM_REPORT:
                return "st_device_tag_upstream_report";
            case DEVICE_TAG_UPSTREAM_DELETE:
                return "st_device_tag_upstream_delete";
            case SHADOW_UPSTREAM_REPORT:
                return "st_shadow_upstream_report";
            case CONFIG_UPSTREAM_QUERY:
                return "st_config_upstream_query";
            case NTP_UPSTREAM_REQUEST:
                return "st_ntp_upstream_request";
            case OTA_UPSTREAM_VERSION_REPORT:
                return "st_ota_upstream_version_report";
            case OTA_UPSTREAM_PROGRESS_REPORT:
                return "st_ota_upstream_progress_report";
            case OTA_UPSTREAM_FIRMWARE_QUERY:
                return "st_ota_upstream_firmware_query";
            case LOG_UPSTREAM_REPORT:
                return "st_log_upstream_report";
            default:
                return null;
        }
    }

    /**
     * 构建子表名称
     *
     * @param superTableName 超级表名称
     * @param deviceId       设备ID
     * @return 子表名称
     */
    private String buildTableName(String superTableName, String deviceId) {
        // TDengine 表名仅允许字母数字下划线
        String safeId = StrUtil.blankToDefault(deviceId, "unknown")
                .replaceAll("[^A-Za-z0-9_]", "_");
        if (!Character.isLetter(safeId.charAt(0)) && safeId.charAt(0) != '_') {
            safeId = "d_" + safeId;
        }
        return superTableName + "_" + safeId;
    }

    /**
     * 构建Schema字段值列表
     *
     * @param message   设备消息
     * @param topicEnum Topic枚举
     * @return 字段值列表
     */
    private List<Fields> buildSchemaFieldValues(IotDeviceMessage message, IotDeviceTopicEnum topicEnum) {
        List<Fields> fields = new ArrayList<>();

        // 时间戳字段（TDEngine要求第一个字段必须是timestamp；DDL 为 TIMESTAMP）
        LocalDateTime reportTime = message.getReportTime() != null
                ? message.getReportTime() : LocalDateTime.now();
        long millis = reportTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        fields.add(new Fields("ts", millis, DataTypeEnum.TIMESTAMP, null));
        fields.add(new Fields("report_time", millis, DataTypeEnum.TIMESTAMP, null));

        // device_id
        fields.add(new Fields("device_id", message.getDeviceId(), DataTypeEnum.BIGINT, null));

        // server_id
        fields.add(new Fields("server_id", escapeSqlText(message.getServerId()), DataTypeEnum.NCHAR, null));

        // request_id
        fields.add(new Fields("request_id", escapeSqlText(message.getRequestId()), DataTypeEnum.NCHAR, null));

        // method
        fields.add(new Fields("method", escapeSqlText(message.getMethod()), DataTypeEnum.NCHAR, null));

        // params（JSON格式）
        if (hasParamsColumn(topicEnum) && message.getParams() != null) {
            fields.add(new Fields("params", escapeSqlText(JSONUtil.toJsonStr(message.getParams())), DataTypeEnum.NCHAR, null));
        }

        // data（JSON格式）
        if (message.getData() != null) {
            fields.add(new Fields("data", escapeSqlText(JSONUtil.toJsonStr(message.getData())), DataTypeEnum.NCHAR, null));
        }

        // code
        fields.add(new Fields("code", message.getCode(), DataTypeEnum.INT, null));

        // msg
        fields.add(new Fields("msg", escapeSqlText(message.getMsg()), DataTypeEnum.NCHAR, null));

        // topic
        fields.add(new Fields("topic", escapeSqlText(message.getTopic()), DataTypeEnum.NCHAR, null));

        return fields;
    }

    private boolean hasParamsColumn(IotDeviceTopicEnum topicEnum) {
        return topicEnum != IotDeviceTopicEnum.PROPERTY_UPSTREAM_DESIRED_SET_ACK
                && topicEnum != IotDeviceTopicEnum.PROPERTY_UPSTREAM_DESIRED_QUERY_RESPONSE
                && topicEnum != IotDeviceTopicEnum.SERVICE_UPSTREAM_INVOKE_RESPONSE;
    }

    /**
     * 构建Tags字段值列表
     *
     * @param message   设备消息
     * @param topicEnum Topic枚举
     * @return Tags字段值列表
     */
    private List<Fields> buildTagsFieldValues(IotDeviceMessage message, IotDeviceTopicEnum topicEnum) {
        List<Fields> tags = new ArrayList<>();

        // device_identification
        String deviceIdentification = extractDeviceIdentification(message);
        tags.add(new Fields("device_identification", escapeSqlText(deviceIdentification), DataTypeEnum.NCHAR, null));

        // tenant_id
        tags.add(new Fields("tenant_id", message.getTenantId(), DataTypeEnum.BIGINT, null));

        // product_identification
        String productIdentification = extractProductIdentification(message);
        tags.add(new Fields("product_identification", escapeSqlText(productIdentification), DataTypeEnum.NCHAR, null));

        // identifier（用于事件上报和服务调用）
        if (topicEnum == IotDeviceTopicEnum.EVENT_UPSTREAM_REPORT
                || topicEnum == IotDeviceTopicEnum.SERVICE_UPSTREAM_INVOKE_RESPONSE) {
            String identifier = extractIdentifier(message.getTopic());
            tags.add(new Fields("identifier", escapeSqlText(identifier), DataTypeEnum.NCHAR, null));
        }

        return tags;
    }

    /**
     * 从Topic中提取产品标识
     *
     * @param message 设备消息
     * @return 产品标识
     */
    private String extractProductIdentification(IotDeviceMessage message) {
        if (StrUtil.isNotBlank(message.getTopic())) {
            String[] parts = message.getTopic().split("/");
            if (parts.length >= 4 && "iot".equals(parts[1])) {
                return parts[2];
            }
            // 保留原来的
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        return "";
    }

    /**
     * 从Topic中提取设备标识
     *
     * @param message 设备消息
     * @return 设备标识
     */
    private String extractDeviceIdentification(IotDeviceMessage message) {
        if (StrUtil.isNotBlank(message.getTopic())) {
            String[] parts = message.getTopic().split("/");
            if (parts.length >= 4 && "iot".equals(parts[1])) {
                return parts[3];
            }
            if (parts.length >= 3) {
                return parts[2];
            }
        }
        return "";
    }

    /**
     * 从日志 params 中提取字段；params 非 Map 时返回 defaultValue。
     */
    private String extractLogField(Object params, String key, String defaultValue) {
        if (!(params instanceof Map)) {
            return defaultValue;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) params;
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return StrUtil.isBlank(text) || "null".equalsIgnoreCase(text) ? defaultValue : text;
    }

    /**
     * 从Topic中提取identifier（用于事件上报和服务调用）
     *
     * @param topic Topic字符串
     * @return identifier
     */
    private String extractIdentifier(String topic) {
        if (StrUtil.isNotBlank(topic)) {
            String[] parts = topic.split("/");
            // 事件和服务上行 Topic 的 identifier 都位于第 8 段（包含开头空段）。
            if (parts.length > 7) {
                return parts[7];
            }
        }
        return "";
    }

    private String escapeSqlText(Object value) {
        if (value == null) {
            return null;
        }
        // 单引号按 SQL 转义；反斜杠先转义，避免 TDengine/REST 路径破坏 JSON
        return value.toString()
                .replace("\\", "\\\\")
                .replace("'", "''");
    }
}

