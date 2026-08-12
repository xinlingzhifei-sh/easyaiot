package com.basiclab.iot.message.sendlogic.msgmaker;

import com.basiclab.iot.message.domain.entity.TMsgFeishu;
import com.basiclab.iot.message.mapper.TMsgFeishuMapper;
import com.basiclab.iot.message.service.MessageRecordResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 飞书消息加工器
 *
 * @author reese
 * @email reese
 * @since 2024-12-04
 */
@Component
public class FeishuMsgMaker extends BaseMsgMaker implements IMsgMaker {

    @Autowired
    private TMsgFeishuMapper tMsgFeishuMapper;

    @Autowired
    private MessageRecordResolver messageRecordResolver;

    /**
     * 准备(界面字段等)
     */
    @Override
    public void prepare() {
        // 初始化准备逻辑
    }

    /**
     * 组织消息
     *
     * @param msgId 消息ID
     * @return TMsgFeishu
     */
    @Override
    public TMsgFeishu makeMsg(String msgId) {
        TMsgFeishu tMsgFeishu = messageRecordResolver.resolveFeishu(msgId);
        return tMsgFeishu;
    }
}

