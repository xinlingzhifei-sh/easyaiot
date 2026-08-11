package com.basiclab.iot.sink.dal.dataobject;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * Alert实体类（对应VIDEO数据库中的alert表）
 *
 * @author reese
 * @email reese
 */
@Data
public class AlertDO {

    /**
     * 所属租户ID。
     */
    private Long tenantId;

    /**
     * 告警ID（主键，自增）
     */
    private Integer id;

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
     * 详细信息（可以是JSON字符串）
     */
    private String information;

    /**
     * 告警时间（与 Python 端一致：字符串为 Asia/Shanghai 墙钟；入库用带偏移避免 PG/MySQL 与时区组合产生 ±8h）
     */
    private OffsetDateTime time;

    /**
     * 设备ID
     */
    private String deviceId;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 本地图片路径（算法落盘等）
     */
    private String imagePath;

    /**
     * MinIO 图片下载地址（/api/v1/buckets/.../objects/download?prefix=...），由 iot-sink 上传后写入
     */
    private String imageUrl;

    /**
     * 录像路径
     */
    private String recordPath;
    
    /**
     * 告警事件类型[realtime:实时算法任务,snap:抓拍算法任务]
     */
    private String taskType;
    
    /**
     * 通知人列表（JSON格式，格式：[{"phone": "xxx", "email": "xxx", "name": "xxx"}, ...]）
     */
    private String notifyUsers;
    
    /**
     * 通知渠道配置（JSON格式，格式：[{"method": "sms", "template_id": "xxx"}, ...]）
     */
    private String channels;
    
    /**
     * 是否已发送通知
     */
    private Boolean notificationSent;
    
    /**
     * 通知发送时间
     */
    private LocalDateTime notificationSentTime;

    /**
     * 关联事件ID（同一帧算法告警/人脸/车牌）
     */
    private String correlationId;

    private Integer taskId;

    private String taskName;

    /** 边缘节点 edge_node.id */
    private Long edgeNodeId;

    private String edgeNodeName;

    private String edgeNodeHost;

    /** 运行 compute_node.id */
    private Long nodeId;
}

