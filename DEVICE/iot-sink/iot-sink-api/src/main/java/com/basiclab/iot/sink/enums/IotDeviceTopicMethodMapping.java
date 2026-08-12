package com.basiclab.iot.sink.enums;

import lombok.Getter;

/**
 * IotDeviceTopicMethodMapping
 *
 * @author reese
 * @email reese
 */
public class IotDeviceTopicMethodMapping {

    /**
     * 获取 Topic 对应的 Method
     *
     * @param topicEnum Topic 枚举
     * @return Method 字符串，如果未找到则返回 null
     */
    public static String getMethodByTopic(IotDeviceTopicEnum topicEnum) {
        if (topicEnum == null) {
            return null;
        }

        switch (topicEnum) {
            // ========== 属性相关 ==========
            case PROPERTY_UPSTREAM_REPORT:
            case PROPERTY_DOWNSTREAM_REPORT_ACK:
                return IotDeviceMessageMethodEnum.PROPERTY_POST.getMethod();

            case PROPERTY_DOWNSTREAM_DESIRED_SET:
            case PROPERTY_UPSTREAM_DESIRED_SET_ACK:
            case PROPERTY_DOWNSTREAM_DESIRED_QUERY:
            case PROPERTY_UPSTREAM_DESIRED_QUERY_RESPONSE:
                return IotDeviceMessageMethodEnum.PROPERTY_SET.getMethod();

            // ========== 事件相关 ==========
            case EVENT_UPSTREAM_REPORT:
            case EVENT_DOWNSTREAM_REPORT_ACK:
                return IotDeviceMessageMethodEnum.EVENT_POST.getMethod();

            // ========== 日志相关 ==========
            case LOG_UPSTREAM_REPORT:
            case LOG_DOWNSTREAM_REPORT_ACK:
                return IotDeviceMessageMethodEnum.LOG_POST.getMethod();

            // ========== 服务调用相关 ==========
            case SERVICE_DOWNSTREAM_INVOKE:
            case SERVICE_UPSTREAM_INVOKE_RESPONSE:
                return IotDeviceMessageMethodEnum.SERVICE_INVOKE.getMethod();

            // ========== 配置相关 ==========
            case CONFIG_DOWNSTREAM_PUSH:
            case CONFIG_DOWNSTREAM_QUERY_ACK:
            case CONFIG_UPSTREAM_QUERY:
                return IotDeviceMessageMethodEnum.CONFIG_PUSH.getMethod();

            // ========== OTA 相关 ==========
            case OTA_DOWNSTREAM_UPGRADE_TASK:
                return IotDeviceMessageMethodEnum.OTA_UPGRADE.getMethod();

            case OTA_UPSTREAM_PROGRESS_REPORT:
            case OTA_UPSTREAM_VERSION_REPORT:
            case OTA_UPSTREAM_FIRMWARE_QUERY:
                return IotDeviceMessageMethodEnum.OTA_PROGRESS.getMethod();

            // ========== 网关拓扑 ==========
            case TOPO_UPSTREAM_ADD:
            case TOPO_DOWNSTREAM_ADD_ACK:
                return IotDeviceMessageMethodEnum.TOPOLOGY_ADD.getMethod();
            case TOPO_UPSTREAM_DELETE:
            case TOPO_DOWNSTREAM_DELETE_ACK:
                return IotDeviceMessageMethodEnum.TOPOLOGY_DELETE.getMethod();
            case TOPO_UPSTREAM_STATUS:
                return IotDeviceMessageMethodEnum.TOPOLOGY_UPDATE.getMethod();

            // ========== 经网关代理的子设备数据面 ==========
            case SUB_PROPERTY_UPSTREAM_REPORT:
                return IotDeviceMessageMethodEnum.PROPERTY_POST.getMethod();
            case SUB_EVENT_UPSTREAM_REPORT:
                return IotDeviceMessageMethodEnum.EVENT_POST.getMethod();
            case SUB_SERVICE_DOWNSTREAM_INVOKE:
            case SUB_SERVICE_UPSTREAM_INVOKE_RESPONSE:
                return IotDeviceMessageMethodEnum.SERVICE_INVOKE.getMethod();
            case SUB_PROPERTY_DOWNSTREAM_DESIRED_SET:
            case SUB_PROPERTY_UPSTREAM_DESIRED_SET_ACK:
                return IotDeviceMessageMethodEnum.PROPERTY_SET.getMethod();

            default:
                return null;
        }
    }

    /**
     * 判断 Topic 是否有对应的标准 Method
     *
     * @param topicEnum Topic 枚举
     * @return 是否有对应的 Method
     */
    public static boolean hasMethod(IotDeviceTopicEnum topicEnum) {
        return getMethodByTopic(topicEnum) != null;
    }

    /**
     * Topic 与 Method 的完整映射关系说明
     * <p>
     * 上行 Topic（设备->云端）：
     * - PROPERTY_UPSTREAM_REPORT -> thing.property.post (属性上报)
     * - EVENT_UPSTREAM_REPORT -> thing.event.post (事件上报)
     * - LOG_UPSTREAM_REPORT -> thing.log.post (日志上报)
     * - SERVICE_UPSTREAM_INVOKE_RESPONSE -> thing.service.invoke (服务调用响应)
     * - PROPERTY_UPSTREAM_DESIRED_SET_ACK -> thing.property.set (属性期望值设置确认)
     * - PROPERTY_UPSTREAM_DESIRED_QUERY_RESPONSE -> thing.property.set (属性期望值查询响应)
     * - OTA_UPSTREAM_PROGRESS_REPORT -> thing.ota.progress (OTA 升级进度上报)
     * - OTA_UPSTREAM_VERSION_REPORT -> thing.ota.progress (OTA 版本上报)
     * - OTA_UPSTREAM_FIRMWARE_QUERY -> thing.ota.progress (OTA 固件查询)
     * - CONFIG_UPSTREAM_QUERY -> thing.config.push (配置查询)
     * - SHADOW_UPSTREAM_REPORT -> (设备影子状态上报，可能使用自定义 Method)
     * - DEVICE_TAG_UPSTREAM_REPORT -> (设备标签上报，可能使用自定义 Method)
     * - DEVICE_TAG_UPSTREAM_DELETE -> (设备标签删除，可能使用自定义 Method)
     * - NTP_UPSTREAM_REQUEST -> (NTP 同步请求，可能使用自定义 Method)
     * <p>
     * 下行 Topic（云端->设备）：
     * - PROPERTY_DOWNSTREAM_REPORT_ACK -> thing.property.post (属性上报回复)
     * - EVENT_DOWNSTREAM_REPORT_ACK -> thing.event.post (事件上报回复)
     * - LOG_DOWNSTREAM_REPORT_ACK -> thing.log.post (日志上报回复，通常不需要)
     * - SERVICE_DOWNSTREAM_INVOKE -> thing.service.invoke (服务调用)
     * - PROPERTY_DOWNSTREAM_DESIRED_SET -> thing.property.set (属性期望值设置)
     * - PROPERTY_DOWNSTREAM_DESIRED_QUERY -> thing.property.set (属性期望值查询)
     * - OTA_DOWNSTREAM_UPGRADE_TASK -> thing.ota.upgrade (OTA 固件升级任务)
     * - CONFIG_DOWNSTREAM_PUSH -> thing.config.push (配置推送)
     * - CONFIG_DOWNSTREAM_QUERY_ACK -> thing.config.push (配置查询回复)
     * - SHADOW_DOWNSTREAM_DESIRED -> (设备影子期望值推送，可能使用自定义 Method)
     * - DEVICE_TAG_DOWNSTREAM_REPORT_ACK -> (设备标签上报回复，可能使用自定义 Method)
     * - DEVICE_TAG_DOWNSTREAM_DELETE_ACK -> (设备标签删除回复，可能使用自定义 Method)
     * - NTP_DOWNSTREAM_RESPONSE -> (NTP 同步响应，可能使用自定义 Method)
     * - BROADCAST_DOWNSTREAM -> (广播消息，可能使用自定义 Method)
     */
    public static class MappingDocumentation {
        // 此内部类仅用于文档说明，不包含实际代码
    }
}

