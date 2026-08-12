package com.basiclab.iot.system.framework.sms.core.client.dto;

import com.basiclab.iot.system.framework.sms.core.enums.SmsTemplateAuditStatusEnum;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 短信模板 Response DTO
 *
 * @author reese
 * @email reese
 */
@Data
@Accessors(chain = true)
public class SmsTemplateRespDTO {

    /**
     * 模板编号
     */
    private String id;
    /**
     * 短信内容
     */
    private String content;
    /**
     * 审核状态
     *
     * 枚举 {@link SmsTemplateAuditStatusEnum}
     */
    private Integer auditStatus;
    /**
     * 审核未通过的理由
     */
    private String auditReason;

}
