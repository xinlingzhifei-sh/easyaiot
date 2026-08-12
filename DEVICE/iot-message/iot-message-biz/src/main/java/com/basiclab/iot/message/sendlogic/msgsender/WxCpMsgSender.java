package com.basiclab.iot.message.sendlogic.msgsender;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.basiclab.iot.message.domain.entity.MessageConfig;
import com.basiclab.iot.message.domain.entity.TMsgWxCp;
import com.basiclab.iot.message.domain.model.SendResult;
import com.basiclab.iot.message.mapper.TMsgWxCpMapper;
import com.basiclab.iot.message.service.MessageRecordResolver;
import com.basiclab.iot.message.mapper.TPreviewUserGroupMapper;
import com.basiclab.iot.message.mapper.TPreviewUserMapper;
import com.basiclab.iot.message.sendlogic.msgmaker.WxCpMsgMaker;
import com.basiclab.iot.message.service.MessageConfigService;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.util.http.apache.DefaultApacheHttpClientBuilder;
import me.chanjar.weixin.cp.api.WxCpService;
import me.chanjar.weixin.cp.api.impl.WxCpServiceApacheHttpClientImpl;
import me.chanjar.weixin.cp.bean.message.WxCpMessage;
import me.chanjar.weixin.cp.bean.message.WxCpMessageSendResult;
import me.chanjar.weixin.cp.config.impl.WxCpDefaultConfigImpl;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 企业微信模板消息发送器
 *
 * @author reese
 * @email reese
 * @since 2024-07-19
 */
@Slf4j
@Component
public class WxCpMsgSender implements IMsgSender {
    private static final String WXCP_ROBOT_TYPE = "群机器人消息";

    @Autowired
    private WxCpMsgMaker wxCpMsgMaker;

    @Autowired
    private MessageConfigService messageConfigService;

    @Autowired
    private TMsgWxCpMapper tMsgWxCpMapper;

    @Autowired
    private MessageRecordResolver messageRecordResolver;

    @Autowired
    private TPreviewUserMapper tPreviewUserMapper;

    @Autowired
    private TPreviewUserGroupMapper tPreviewUserGroupMapper;

    @Override
    public SendResult send(String msgId) {
        log.info("微信发送开始 params is:" + msgId);
        SendResult sendResult = new SendResult();
        TMsgWxCp tMsgWxCp = messageRecordResolver.resolveWxCp(msgId);
        if (tMsgWxCp == null) {
            sendResult.setSuccess(false);
            sendResult.setInfo("企业微信消息不存在: " + msgId);
            return sendResult;
        }
        if (isRobotMessage(tMsgWxCp)) {
            return sendRobotMsg(tMsgWxCp);
        }
        return sendWorkMsg(tMsgWxCp);
    }

    /**
     * 企业微信应用工作通知发送（支持模板内 previewUser 或用户组）
     */
    public SendResult sendWorkMsg(TMsgWxCp tMsgWxCp) {
        SendResult sendResult = new SendResult();
        sendResult.setMsgName(tMsgWxCp.getMsgName());
        try {
            List<String> previewUsers = resolvePreviewUsers(tMsgWxCp);
            if (previewUsers.isEmpty()) {
                sendResult.setSuccess(false);
                sendResult.setInfo("未配置收件人（用户分组或 previewUser）");
                return sendResult;
            }

            WxCpService wxCpService = getWxCpService(tMsgWxCp.getAgentId());
            WxCpMessage wxCpMessage = wxCpMsgMaker.buildMessage(tMsgWxCp);
            if (wxCpMessage == null) {
                sendResult.setSuccess(false);
                sendResult.setInfo("企业微信消息构建失败");
                return sendResult;
            }

            WxCpMessageSendResult wxCpMessageSendResult = new WxCpMessageSendResult();
            for (String openId : previewUsers) {
                wxCpMessage.setToUser(openId);
                wxCpMessageSendResult = wxCpService.getMessageService().send(wxCpMessage);
            }
            if (wxCpMessageSendResult.getErrCode() != 0
                    || StringUtils.isNotEmpty(wxCpMessageSendResult.getInvalidUser())) {
                sendResult.setSuccess(false);
                sendResult.setInfo(wxCpMessageSendResult.toString());
                log.error(wxCpMessageSendResult.toString());
                return sendResult;
            }
            sendResult.setSuccess(true);
        } catch (Exception e) {
            sendResult.setSuccess(false);
            sendResult.setInfo(e.getMessage());
            log.error(ExceptionUtils.getStackTrace(e));
        }
        return sendResult;
    }

    private List<String> resolvePreviewUsers(TMsgWxCp tMsgWxCp) {
        Set<String> users = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(tMsgWxCp.getPreviewUser())) {
            users.add(tMsgWxCp.getPreviewUser().trim());
        }
        String userGroupId = tMsgWxCp.getUserGroupId();
        if (StringUtils.isNotEmpty(userGroupId)) {
            String previewUserId = tPreviewUserGroupMapper.queryPreviewUserIds(userGroupId);
            if (StringUtils.isNotBlank(previewUserId)) {
                List<String> previewUserIds = Arrays.asList(previewUserId.split(","));
                users.addAll(tPreviewUserMapper.queryPreviewUsers(previewUserIds));
            }
        }
        return new ArrayList<>(users);
    }

    /**
     * 企业微信群机器人 Webhook 发送
     */
    public SendResult sendRobotMsg(TMsgWxCp tMsgWxCp) {
        SendResult sendResult = new SendResult();
        sendResult.setMsgName(tMsgWxCp.getMsgName());
        try {
            String webHook = tMsgWxCp.getWebHook();
            if (StringUtils.isBlank(webHook)) {
                sendResult.setSuccess(false);
                sendResult.setInfo("企业微信群机器人 Webhook 地址不能为空");
                return sendResult;
            }

            JSONObject messageBody = buildRobotMessage(tMsgWxCp);
            HttpResponse response = HttpRequest.post(webHook)
                    .header("Content-Type", "application/json")
                    .body(messageBody.toJSONString())
                    .timeout(10000)
                    .execute();

            String responseBody = response.body();
            log.info("企业微信群机器人发送响应: status={}, body={}", response.getStatus(), responseBody);

            if (!response.isOk()) {
                sendResult.setSuccess(false);
                sendResult.setInfo("HTTP请求失败: " + response.getStatus() + ", " + responseBody);
                return sendResult;
            }

            JSONObject responseJson = JSONObject.parseObject(responseBody);
            Integer errCode = responseJson.getInteger("errcode");
            if (errCode != null && errCode == 0) {
                sendResult.setSuccess(true);
                sendResult.setInfo("发送成功");
            } else {
                sendResult.setSuccess(false);
                sendResult.setInfo(responseJson.getString("errmsg") != null
                        ? responseJson.getString("errmsg") : responseBody);
            }
        } catch (Exception e) {
            sendResult.setSuccess(false);
            sendResult.setInfo(e.getMessage());
            log.error("企业微信群机器人发送失败: {}", ExceptionUtils.getStackTrace(e));
        }
        return sendResult;
    }

    public static boolean isRobotMessage(TMsgWxCp template) {
        if (template == null) {
            return false;
        }
        if (StringUtils.isNotBlank(template.getWebHook())) {
            return true;
        }
        return WXCP_ROBOT_TYPE.equals(template.getRadioType());
    }

    private JSONObject buildRobotMessage(TMsgWxCp tMsgWxCp) {
        JSONObject message = new JSONObject();
        String msgType = tMsgWxCp.getCpMsgType();

        if ("markdown消息".equals(msgType)) {
            JSONObject markdown = new JSONObject();
            markdown.put("content", StringUtils.defaultString(tMsgWxCp.getContent()));
            message.put("msgtype", "markdown");
            message.put("markdown", markdown);
            return message;
        }

        if ("图文消息".equals(msgType)) {
            JSONObject news = new JSONObject();
            JSONArray articles = new JSONArray();
            JSONObject article = new JSONObject();
            article.put("title", StringUtils.defaultString(tMsgWxCp.getTitle()));
            article.put("description", StringUtils.defaultString(tMsgWxCp.getDescribe()));
            article.put("url", StringUtils.defaultString(tMsgWxCp.getUrl()));
            article.put("picurl", StringUtils.defaultString(tMsgWxCp.getImgUrl()));
            articles.add(article);
            news.put("articles", articles);
            message.put("msgtype", "news");
            message.put("news", news);
            return message;
        }

        JSONObject text = new JSONObject();
        text.put("content", StringUtils.defaultString(tMsgWxCp.getContent()));
        message.put("msgtype", "text");
        message.put("text", text);
        return message;
    }

    @Override
    public SendResult asyncSend(String[] msgData) {
        return null;
    }

    /**
     * 按模板 agentId 匹配 message_config 中对应应用的 secret
     */
    private WxCpDefaultConfigImpl wxCpConfigStorage(String agentIdStr) {
        MessageConfig messageConfig = messageConfigService.queryByMsgType(4);
        Map<String, Object> configMap = messageConfig.getConfigurationMap();
        JSONArray jsonArray = (JSONArray) configMap.get("wxCpApp");
        JSONObject jsonObject = null;
        if (jsonArray != null && !jsonArray.isEmpty()) {
            if (StringUtils.isNotBlank(agentIdStr)) {
                for (int i = 0; i < jsonArray.size(); i++) {
                    JSONObject app = jsonArray.getJSONObject(i);
                    if (agentIdStr.equals(app.getString("agentId"))) {
                        jsonObject = app;
                        break;
                    }
                }
            }
            if (jsonObject == null) {
                jsonObject = jsonArray.getJSONObject(0);
            }
        } else {
            jsonObject = new JSONObject();
        }
        WxCpDefaultConfigImpl configStorage = new WxCpDefaultConfigImpl();
        configStorage.setCorpId((String) configMap.get("wxCpCorpId"));
        String agentId = jsonObject.getString("agentId");
        if (StringUtils.isNotBlank(agentId)) {
            configStorage.setAgentId(Integer.valueOf(agentId));
        } else if (StringUtils.isNotBlank(agentIdStr)) {
            configStorage.setAgentId(Integer.valueOf(agentIdStr));
        }
        configStorage.setCorpSecret(jsonObject.getString("secret"));
        DefaultApacheHttpClientBuilder clientBuilder = DefaultApacheHttpClientBuilder.get();
        clientBuilder.setConnectionRequestTimeout(10000);
        clientBuilder.setConnectionTimeout(5000);
        clientBuilder.setSoTimeout(5000);
        clientBuilder.setIdleConnTimeout(60000);
        clientBuilder.setCheckWaitTime(60000);
        clientBuilder.setMaxConnPerHost(100);
        clientBuilder.setMaxTotalConn(100);
        configStorage.setApacheHttpClientBuilder(clientBuilder);
        return configStorage;
    }

    public WxCpService getWxCpService(String agentIdStr) {
        WxCpDefaultConfigImpl wxCpConfigStorage = wxCpConfigStorage(agentIdStr);
        WxCpService wxCpService = new WxCpServiceApacheHttpClientImpl();
        wxCpService.setWxCpConfigStorage(wxCpConfigStorage);
        return wxCpService;
    }
}
