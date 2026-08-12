package com.basiclab.iot.message.sendlogic.msgmaker;

import com.basiclab.iot.message.domain.entity.TMsgMail;
import com.basiclab.iot.message.mapper.TMsgMailMapper;
import com.basiclab.iot.message.service.MessageRecordResolver;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * email-消息加工器
 *
 * @author reese
 * @email reese
 * @since 2024-07-17
 */
@Component
public class MailMsgMaker {

    @Autowired
    private TMsgMailMapper tMsgMailMapper;

    @Autowired
    private MessageRecordResolver messageRecordResolver;

    /**
     * 组织E-Mail消息
     *
     * @param msgId 消息信息
     * @return MailMsg
     */
    public TMsgMail makeMsg(String msgId, String mailContent) {
       TMsgMail tMsgMail = messageRecordResolver.resolveMail(msgId);
       if (tMsgMail == null) {
           throw new RuntimeException("邮件消息不存在: msgId=" + msgId);
       }
       if(StringUtils.isNotEmpty(mailContent)){
           tMsgMail.setContent(mailContent);
       }
        return tMsgMail;
    }
}
