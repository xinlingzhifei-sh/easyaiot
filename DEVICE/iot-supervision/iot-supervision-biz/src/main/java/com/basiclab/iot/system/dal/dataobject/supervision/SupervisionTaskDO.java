package com.basiclab.iot.system.dal.dataobject.supervision;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.basiclab.iot.common.core.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("system_supervision_task")
@KeySequence("system_supervision_task_id_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SupervisionTaskDO extends BaseDO {

    @TableId
    private Long id;
    private Long eventId;
    private String taskNo;
    private String taskType;
    private String taskStatus;
    private Long assignedDeptId;
    private String assignedRole;
    private Long assignedUserId;
    private LocalDateTime dueAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime arrivedAt;
    private LocalDateTime submittedAt;
    private String resultCategory;
    private String handlingNote;
    private Integer reworkCount;

}
