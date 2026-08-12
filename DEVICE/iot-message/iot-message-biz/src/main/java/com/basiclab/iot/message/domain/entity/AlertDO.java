package com.basiclab.iot.message.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Alert实体类（对应VIDEO数据库中的alert表）
 *
 * @author reese
 * @email reese
 */
@Data
public class AlertDO {

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
     * 告警时间
     */
    private LocalDateTime time;

    /**
     * 设备ID
     */
    private String deviceId;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 本地图片路径
     */
    private String imagePath;

    /**
     * MinIO 图片下载地址
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
}

