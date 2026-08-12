package com.basiclab.iot.message.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 告警通知消息DTO
 *
 * @author reese
 * @email reese
 */
@Data
public class AlertNotificationMessage {
    
    /**
     * 告警ID
     */
    @JsonAlias("alert_id")
    private Integer alertId;
    
    /**
     * 任务ID
     */
    @JsonAlias("task_id")
    private Integer taskId;
    
    /**
     * 任务名称
     */
    @JsonAlias("task_name")
    private String taskName;
    
    /**
     * 设备ID
     */
    @JsonAlias("device_id")
    private String deviceId;
    
    /**
     * 设备名称
     */
    @JsonAlias("device_name")
    private String deviceName;
    
    /**
     * 告警信息
     */
    private AlertInfo alert;
    
    /**
     * 通知人列表
     */
    private List<Map<String, Object>> notifyUsers;
    
    /**
     * 通知方式列表
     * 支持6种通知方式：
     * - sms: 短信（阿里云/腾讯云）
     * - email: 邮件
     * - wxcp/wechat/weixin: 企业微信
     * - http/webhook: HTTP请求
     * - ding/dingtalk: 钉钉
     * - feishu/lark: 飞书
     */
    private List<String> notifyMethods;
    
    /**
     * 通知渠道和模板配置列表
     * 格式：[{"method": "sms", "template_id": "xxx", "template_name": "xxx"}, ...]
     */
    private List<Map<String, Object>> channels;
    
    /**
     * 时间戳
     */
    private String timestamp;
    
    /**
     * 是否需要发送通知
     * true: 需要发送通知（有通知配置且通知人列表不为空）
     * false: 不需要发送通知（没有通知配置或通知人列表为空）
     */
    private Boolean shouldNotify;
    
    /**
     * 告警信息内部类
     */
    @Data
    public static class AlertInfo {
        /**
         * 对象类型
         */
        private String object;
        
        /**
         * 事件类型
         */
        private String event;
        
        /**
         * 区域
         */
        private String region;
        
        /**
         * 详细信息
         */
        private Object information;
        
        /**
         * 图片路径
         */
        @JsonAlias("image_path")
        private String imagePath;
        
        /**
         * 录像路径
         */
        @JsonAlias("record_path")
        private String recordPath;
        
        /**
         * 告警时间
         */
        private String time;
        
        /**
         * 告警事件类型[realtime:实时算法任务,snap:抓拍算法任务,threshold:设备阈值]
         */
        @JsonAlias("task_type")
        private String taskType;

        /** 设备属性标识（阈值告警） */
        @JsonAlias({"property_code", "property"})
        private String property;

        /** 设备属性名称（阈值告警） */
        @JsonAlias("property_name")
        private String propertyName;

        /** 触发时的属性值（阈值告警） */
        @JsonAlias("alarm_value")
        private String value;

        /** 告警级别 INFO/WARNING/CRITICAL */
        @JsonAlias("alarm_level")
        private String alarmLevel;
    }
}

