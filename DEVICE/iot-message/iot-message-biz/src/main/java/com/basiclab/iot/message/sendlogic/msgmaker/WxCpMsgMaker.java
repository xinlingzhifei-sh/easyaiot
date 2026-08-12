package com.basiclab.iot.message.sendlogic.msgmaker;

import com.basiclab.iot.message.domain.entity.TMsgWxCp;
import com.basiclab.iot.message.mapper.TMsgWxCpMapper;
import com.basiclab.iot.message.service.MessageRecordResolver;
import me.chanjar.weixin.cp.bean.article.NewArticle;
import me.chanjar.weixin.cp.bean.message.WxCpMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 企业微信消息加工器
 *
 * @author reese
 * @email reese
 * @since 2024-07-18
 */
@Component
public class WxCpMsgMaker extends BaseMsgMaker implements IMsgMaker {

    private static String agentId;

    public static String msgType;

    private static String msgTitle;

    private static String picUrl;

    public static String desc;

    public static String url;

    private static String btnTxt;

    private static String msgContent;

    @Autowired
    private TMsgWxCpMapper tMsgWxCpMapper;

    @Autowired
    private MessageRecordResolver messageRecordResolver;

    /**
     * 准备(界面字段等)
     */
    @Override
    public void prepare() {
        String agentIdBefore = agentId;
        String agentIdNow = "";
        synchronized (this) {
            if (agentIdBefore == null || !agentIdBefore.equals(agentIdNow)) {
                agentId = agentIdNow;
            }
        }
        msgType = "";
        msgTitle = "";
        picUrl = "";
        desc = "";
        url = "";
        btnTxt = "";
        msgContent = "";
    }

    /**
     * 组织消息-企业微信
     *
     * @param msgId 消息数据
     * @return WxMpTemplateMessage
     */
    @Override
    public WxCpMessage makeMsg(String msgId) {
        TMsgWxCp tMsgWxCp = messageRecordResolver.resolveWxCp(msgId);
        if (tMsgWxCp == null) {
            return null;
        }
        return buildMessage(tMsgWxCp);
    }

    /**
     * 根据模板实体构建企业微信应用消息（手动推送与告警共用）
     */
    public WxCpMessage buildMessage(TMsgWxCp tMsgWxCp) {
        if (tMsgWxCp == null || tMsgWxCp.getAgentId() == null || tMsgWxCp.getAgentId().trim().isEmpty()) {
            return null;
        }
        int agentId = Integer.parseInt(tMsgWxCp.getAgentId().trim());
        String toUser = tMsgWxCp.getPreviewUser() != null ? tMsgWxCp.getPreviewUser() : "";
        String msgType = tMsgWxCp.getCpMsgType();
        if ("图文消息".equals(msgType)) {
            NewArticle article = new NewArticle();
            article.setTitle(tMsgWxCp.getTitle());
            article.setPicUrl(tMsgWxCp.getImgUrl());
            article.setDescription(tMsgWxCp.getDescribe());
            article.setUrl(tMsgWxCp.getUrl());
            return WxCpMessage.NEWS().agentId(agentId).toUser(toUser).addArticle(article).build();
        }
        if ("文本消息".equals(msgType)) {
            return WxCpMessage.TEXT().agentId(agentId).toUser(toUser)
                    .content(tMsgWxCp.getContent()).build();
        }
        if ("markdown消息".equals(msgType)) {
            return WxCpMessage.MARKDOWN().agentId(agentId).toUser(toUser)
                    .content(tMsgWxCp.getContent()).build();
        }
        if ("文本卡片消息".equals(msgType)) {
            return WxCpMessage.TEXTCARD().agentId(agentId).toUser(toUser)
                    .title(tMsgWxCp.getTitle())
                    .description(tMsgWxCp.getDescribe())
                    .url(tMsgWxCp.getUrl())
                    .btnTxt(tMsgWxCp.getBtnTxt())
                    .build();
        }
        return null;
    }
}
