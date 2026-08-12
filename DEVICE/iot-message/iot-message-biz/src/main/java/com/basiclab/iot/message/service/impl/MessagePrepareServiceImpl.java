package com.basiclab.iot.message.service.impl;

import com.basiclab.iot.message.domain.entity.*;
import com.basiclab.iot.message.domain.enums.MessageRecordTypeEnum;
import com.basiclab.iot.message.domain.model.vo.MessagePrepareVO;
import com.basiclab.iot.message.mapper.*;
import com.basiclab.iot.message.service.MessagePrepareService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 消息准备实现层Impl
 *
 * @author reese
 * @email reese
 * @since 2024-07-18
 */
@Slf4j
@Component
public class MessagePrepareServiceImpl implements MessagePrepareService {

    @Autowired
    private TMsgMailMapper tMsgMailMapper;
    @Autowired
    private TMsgDingMapper tMsgDingMapper;
    @Autowired
    private TMsgHttpMapper tMsgHttpMapper;
    @Autowired
    private TMsgSmsMapper tMsgSmsMapper;
    @Autowired
    private TMsgWxCpMapper tMsgWxCpMapper;
    @Autowired
    private TMsgFeishuMapper tMsgFeishuMapper;
    @Autowired
    private TTemplateDataMapper templateDataMapper;
    @Autowired
    private TPushHistoryMapper tPushHistoryMapper;

    @Autowired
    private TPreviewUserGroupMapper tPreviewUserGroupMapper;


    @Override
    public MessagePrepareVO add(MessagePrepareVO messagePrepareVO) {
        int msgType = messagePrepareVO.getMsgType();
        switch (msgType){
            case 1 :
                return addSmsMessage(messagePrepareVO,1);
            case 2 :
                return addSmsMessage(messagePrepareVO,2);
            case 3 :
                TMsgMail tMsgMail = messagePrepareVO.getT_Msg_Mail();
                if (tMsgMail.getId() == null || tMsgMail.getId().isEmpty()) {
                    tMsgMail.setId(UUID.randomUUID().toString());
                }
                tMsgMail.setCreateTime(new Date());
                tMsgMail.setRecordType(MessageRecordTypeEnum.PUSH);
                clearMailContentForPush(tMsgMail);
                tMsgMailMapper.insert(tMsgMail);
                messagePrepareVO.setT_Msg_Mail(tMsgMail);
                return messagePrepareVO;
            case 4 :
                TMsgWxCp tMsgWxCp = messagePrepareVO.getT_Msg_Wx_Cp();
                if (tMsgWxCp.getId() == null || tMsgWxCp.getId().isEmpty()) {
                    tMsgWxCp.setId(UUID.randomUUID().toString());
                }
                tMsgWxCp.setCreateTime(new Date());
                tMsgWxCp.setRecordType(MessageRecordTypeEnum.PUSH);
                clearWxCpContentForPush(tMsgWxCp);
                tMsgWxCpMapper.insert(tMsgWxCp);
                messagePrepareVO.setT_Msg_Wx_Cp(tMsgWxCp);
                return messagePrepareVO;
            case 5 :
                TMsgHttp tMsgHttp = messagePrepareVO.getT_Msg_Http();
                if (tMsgHttp.getId() == null || tMsgHttp.getId().isEmpty()) {
                    tMsgHttp.setId(UUID.randomUUID().toString());
                }
                tMsgHttp.setCreateTime(new Date());
                tMsgHttp.setRecordType(MessageRecordTypeEnum.PUSH);
                clearHttpContentForPush(tMsgHttp);
                tMsgHttpMapper.insert(tMsgHttp);
                messagePrepareVO.setT_Msg_Http(tMsgHttp);
                return messagePrepareVO;
            case 6 :
                TMsgDing tMsgDing = messagePrepareVO.getT_Msg_Ding();
                if (tMsgDing.getId() == null || tMsgDing.getId().isEmpty()) {
                    tMsgDing.setId(UUID.randomUUID().toString());
                }
                tMsgDing.setCreateTime(new Date());
                tMsgDing.setRecordType(MessageRecordTypeEnum.PUSH);
                clearDingContentForPush(tMsgDing);
                tMsgDingMapper.insert(tMsgDing);
                messagePrepareVO.setT_Msg_Ding(tMsgDing);
                return messagePrepareVO;
            case 7 :
                TMsgFeishu tMsgFeishu = messagePrepareVO.getT_Msg_Feishu();
                if (tMsgFeishu.getId() == null || tMsgFeishu.getId().isEmpty()) {
                    tMsgFeishu.setId(UUID.randomUUID().toString());
                }
                tMsgFeishu.setCreateTime(new Date());
                tMsgFeishu.setRecordType(MessageRecordTypeEnum.PUSH);
                clearFeishuContentForPush(tMsgFeishu);
                tMsgFeishuMapper.insert(tMsgFeishu);
                messagePrepareVO.setT_Msg_Feishu(tMsgFeishu);
                return messagePrepareVO;
        }
        return messagePrepareVO;
    }

    @NotNull
    private MessagePrepareVO addSmsMessage(MessagePrepareVO messagePrepareVO,int msgType) {
        TMsgSms tMsgSms = messagePrepareVO.getT_Msg_Sms();
        List<TTemplateData> templateDataList = messagePrepareVO.getTemplateDataList();
        // 如果ID已存在，使用已有ID；否则生成新ID
        if (tMsgSms.getId() == null || tMsgSms.getId().isEmpty()) {
            tMsgSms.setId(UUID.randomUUID().toString());
        }
        tMsgSms.setCreateTime(new Date());
        tMsgSms.setRecordType(MessageRecordTypeEnum.PUSH);
        clearSmsContentForPush(tMsgSms);
        tMsgSmsMapper.insert(tMsgSms);
        messagePrepareVO.setT_Msg_Sms(tMsgSms);
        for(TTemplateData templateData : CollectionUtils.emptyIfNull(templateDataList)){
            templateData.setId(UUID.randomUUID().toString());
            templateData.setCreateTime(new Date());
            templateData.setMsgId(tMsgSms.getId());
            templateData.setMsgType(msgType);
            templateDataMapper.insert(templateData);
        }
        return messagePrepareVO;
    }

    @Override
    public MessagePrepareVO update(MessagePrepareVO messagePrepareVO) {
        int msgType = messagePrepareVO.getMsgType();
        switch (msgType){
            case 1 :
                return updateMsgSms(messagePrepareVO,msgType);
            case 2 :
                return updateMsgSms(messagePrepareVO,msgType);
            case 3 :
                TMsgMail tMsgMail = messagePrepareVO.getT_Msg_Mail();
                tMsgMail.setModifiedTime(new Date());
                tMsgMail.setRecordType(MessageRecordTypeEnum.PUSH);
                clearMailContentForPush(tMsgMail);
                tMsgMailMapper.updateByPrimaryKeySelective(tMsgMail);
                messagePrepareVO.setT_Msg_Mail(tMsgMail);
                return messagePrepareVO;
            case 4 :
                TMsgWxCp tMsgWxCp = messagePrepareVO.getT_Msg_Wx_Cp();
                tMsgWxCp.setModifiedTime(new Date());
                tMsgWxCp.setRecordType(MessageRecordTypeEnum.PUSH);
                clearWxCpContentForPush(tMsgWxCp);
                tMsgWxCpMapper.updateByPrimaryKeySelective(tMsgWxCp);
                messagePrepareVO.setT_Msg_Wx_Cp(tMsgWxCp);
                return messagePrepareVO;
            case 5 :
                TMsgHttp tMsgHttp = messagePrepareVO.getT_Msg_Http();
                tMsgHttp.setModifiedTime(new Date());
                tMsgHttp.setRecordType(MessageRecordTypeEnum.PUSH);
                clearHttpContentForPush(tMsgHttp);
                tMsgHttpMapper.updateByPrimaryKeySelective(tMsgHttp);
                messagePrepareVO.setT_Msg_Http(tMsgHttp);
                return messagePrepareVO;
            case 6 :
                TMsgDing tMsgDing = messagePrepareVO.getT_Msg_Ding();
                tMsgDing.setModifiedTime(new Date());
                tMsgDing.setRecordType(MessageRecordTypeEnum.PUSH);
                clearDingContentForPush(tMsgDing);
                tMsgDingMapper.updateByPrimaryKeySelective(tMsgDing);
                messagePrepareVO.setT_Msg_Ding(tMsgDing);
                return messagePrepareVO;
            case 7 :
                TMsgFeishu tMsgFeishu = messagePrepareVO.getT_Msg_Feishu();
                tMsgFeishu.setModifiedTime(new Date());
                tMsgFeishu.setRecordType(MessageRecordTypeEnum.PUSH);
                clearFeishuContentForPush(tMsgFeishu);
                tMsgFeishuMapper.updateByPrimaryKeySelective(tMsgFeishu);
                messagePrepareVO.setT_Msg_Feishu(tMsgFeishu);
                return messagePrepareVO;
        }
        return messagePrepareVO;
    }

    @NotNull
    private MessagePrepareVO updateMsgSms(MessagePrepareVO messagePrepareVO,int msgType) {
        TMsgSms tMsgSms = messagePrepareVO.getT_Msg_Sms();
        tMsgSms.setModifiedTime(new Date());
        tMsgSms.setRecordType(MessageRecordTypeEnum.PUSH);
        clearSmsContentForPush(tMsgSms);
        tMsgSmsMapper.updateByPrimaryKeySelective(tMsgSms);
        templateDataMapper.deleteByMsgTypeAndMsgId(msgType,tMsgSms.getId());
        List<TTemplateData> templateDataList = messagePrepareVO.getTemplateDataList();
        for(TTemplateData templateData : CollectionUtils.emptyIfNull(templateDataList)){
            templateData.setModifiedTime(new Date());
            templateData.setId(UUID.randomUUID().toString());
            templateData.setCreateTime(new Date());
            templateData.setMsgId(tMsgSms.getId());
            templateData.setMsgType(msgType);
            templateDataMapper.insert(templateData);
        }
        messagePrepareVO.setT_Msg_Sms(tMsgSms);
        return messagePrepareVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String delete(int msgType, String id) {
        // 先检查记录是否存在
        boolean recordExists = false;
        switch (msgType){
            case 1 :
            case 2 :
                recordExists = tMsgSmsMapper.selectByPrimaryKey(id) != null;
                break;
            case 3 :
                recordExists = tMsgMailMapper.selectByPrimaryKey(id) != null;
                break;
            case 4 :
                recordExists = tMsgWxCpMapper.selectByPrimaryKey(id) != null;
                break;
            case 5 :
                recordExists = tMsgHttpMapper.selectByPrimaryKey(id) != null;
                break;
            case 6 :
                recordExists = tMsgDingMapper.selectByPrimaryKey(id) != null;
                break;
            case 7 :
                recordExists = tMsgFeishuMapper.selectByPrimaryKey(id) != null;
                break;
            default: 
                return "";
        }
        
        // 如果记录不存在，直接返回（认为删除成功，因为目标状态已经达成）
        if (!recordExists) {
            return id;
        }
        
        // 先删除关联数据：模板数据和推送历史
        templateDataMapper.deleteByMsgTypeAndMsgId(msgType, id);
        tPushHistoryMapper.deleteByMsgIdAndMsgType(id, msgType);
        
        // 再删除主表数据
        int result = 0;
        switch (msgType){
            case 1 :
                result = tMsgSmsMapper.deleteByPrimaryKey(id);
                break;
            case 2 :
                result = tMsgSmsMapper.deleteByPrimaryKey(id);
                break;
            case 3 :
                result = tMsgMailMapper.deleteByPrimaryKey(id);
                break;
            case 4 :
                result = tMsgWxCpMapper.deleteByPrimaryKey(id);
                break;
            case 5 :
                result = tMsgHttpMapper.deleteByPrimaryKey(id);
                break;
            case 6 :
                result = tMsgDingMapper.deleteByPrimaryKey(id);
                break;
            case 7 :
                result = tMsgFeishuMapper.deleteByPrimaryKey(id);
                break;
            default: 
                return "";
        }
        
        // 检查删除结果（如果记录存在但删除失败，抛出异常）
        if (result <= 0) {
            throw new RuntimeException("删除失败，未找到要删除的记录，msgType=" + msgType + ", id=" + id);
        }
        
        return id;
    }

    @Override
    public void deleteMessageInstance(int msgType, String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        // 尽力清理临时实例行，失败不影响告警发送主流程
        try {
            switch (msgType) {
                case 1:
                case 2:
                    templateDataMapper.deleteByMsgTypeAndMsgId(msgType, id);
                    tMsgSmsMapper.deleteByPrimaryKey(id);
                    break;
                case 3:
                    tMsgMailMapper.deleteByPrimaryKey(id);
                    break;
                case 4:
                    tMsgWxCpMapper.deleteByPrimaryKey(id);
                    break;
                case 5:
                    tMsgHttpMapper.deleteByPrimaryKey(id);
                    break;
                case 6:
                    tMsgDingMapper.deleteByPrimaryKey(id);
                    break;
                case 7:
                    tMsgFeishuMapper.deleteByPrimaryKey(id);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            log.warn("清理临时消息实例失败, msgType={}, id={}, error={}", msgType, id, e.getMessage());
        }
    }

    @Override
    public List<?> query(MessagePrepareVO messagePrepareVO) {
        int msgType = messagePrepareVO.getMsgType();
        String msgName = messagePrepareVO.getMsgName();
        switch (msgType){
            case 1:
            case 2:
                return queryMsgSms(msgType, msgName);
            case 3:
                List<TMsgMail> tMsgMails = filterPushRecords(tMsgMailMapper.selectByMsgTypeAndMsgName(msgType,msgName));
                for(TMsgMail tMsgMail : CollectionUtils.emptyIfNull(tMsgMails)){
                    String userGroupName = tPreviewUserGroupMapper.getGroupNameById(tMsgMail.getUserGroupId());
                    tMsgMail.setUserGroupName(userGroupName);
                    fillTemplateName(tMsgMail);
                }
                return tMsgMails;
            case 4:
                List<TMsgWxCp> tMsgWxCps = filterPushRecords(tMsgWxCpMapper.selectByMsgTypeAndMsgName(msgType,msgName));
                for(TMsgWxCp tMsgWxCp : CollectionUtils.emptyIfNull(tMsgWxCps)){
                    String userGroupName = tPreviewUserGroupMapper.getGroupNameById(tMsgWxCp.getUserGroupId());
                    tMsgWxCp.setUserGroupName(userGroupName);
                    fillTemplateName(tMsgWxCp);
                }
                return tMsgWxCps;
            case 5:
                List<TMsgHttp> tMsgHttps = filterPushRecords(tMsgHttpMapper.selectByMsgTypeAndMsgName(msgType,msgName));
                for (TMsgHttp http : CollectionUtils.emptyIfNull(tMsgHttps)) {
                    fillTemplateName(http);
                }
                return tMsgHttps;
            case 6:
                List<TMsgDing> tMsgDings = filterPushRecords(tMsgDingMapper.selectByMsgTypeAndMsgName(msgType,msgName));
                for(TMsgDing tMsgDing : CollectionUtils.emptyIfNull(tMsgDings)){
                    String userGroupName = tPreviewUserGroupMapper.getGroupNameById(tMsgDing.getUserGroupId());
                    tMsgDing.setUserGroupName(userGroupName);
                    fillTemplateName(tMsgDing);
                }
                return tMsgDings;
            case 7:
                List<TMsgFeishu> tMsgFeishus = filterPushRecords(tMsgFeishuMapper.selectByMsgTypeAndMsgName(msgType,msgName));
                for(TMsgFeishu tMsgFeishu : CollectionUtils.emptyIfNull(tMsgFeishus)){
                    String userGroupName = tPreviewUserGroupMapper.getGroupNameById(tMsgFeishu.getUserGroupId());
                    tMsgFeishu.setUserGroupName(userGroupName);
                    fillTemplateName(tMsgFeishu);
                }
                return tMsgFeishus;
            default: return null;
        }
    }

    @Override
    public TMsgSms querySmsByMsgId(String msgId) {
        TMsgSms tMsgSms = tMsgSmsMapper.selectByPrimaryKey(msgId);
        if (tMsgSms == null) {
            return null;
        }
        // 推送记录合并模板内容
        if (tMsgSms.getRecordType() != null && tMsgSms.getRecordType() == MessageRecordTypeEnum.PUSH
                && tMsgSms.getRefTemplateId() != null) {
            TMsgSms template = tMsgSmsMapper.selectByPrimaryKey(tMsgSms.getRefTemplateId());
            if (template != null) {
                tMsgSms.setTemplateId(template.getTemplateId());
                tMsgSms.setContent(template.getContent());
                List<TTemplateData> templateDataList = templateDataMapper.selectByMsgTypeAndMsgId(
                        template.getMsgType(), template.getId());
                tMsgSms.setTemplateDataList(templateDataList);
                return tMsgSms;
            }
        }
        List<TTemplateData> templateDataList = templateDataMapper.selectByMsgId(msgId);
        tMsgSms.setTemplateDataList(templateDataList);
        return tMsgSms;
    }

    @NotNull
    private List<TMsgSms> queryMsgSms(int msgType, String msgName) {
        List<TMsgSms> tMsgSmsList = filterPushRecords(tMsgSmsMapper.selectByMsgTypeAndMsgName(msgType, msgName));
        for(TMsgSms tMsgSms : CollectionUtils.emptyIfNull(tMsgSmsList)){
            String msgId = tMsgSms.getId();
            List<TTemplateData> templateDataList = templateDataMapper.selectByMsgTypeAndMsgId(msgType,msgId);
            tMsgSms.setTemplateDataList(templateDataList);
            String userGroupName = tPreviewUserGroupMapper.getGroupNameById(tMsgSms.getUserGroupId());
            tMsgSms.setUserGroupName(userGroupName);
            fillTemplateName(tMsgSms);
        }
        return tMsgSmsList;
    }

    private List<TMsgSms> enrichPushList(List<TMsgSms> list, int msgType) {
        return list;
    }

    private <T> List<T> filterPushRecords(List<T> list) {
        if (list == null) {
            return list;
        }
        return list.stream().filter(this::isPushRecord).collect(Collectors.toList());
    }

    private boolean isPushRecord(Object entity) {
        Integer recordType = null;
        if (entity instanceof TMsgMail) {
            recordType = ((TMsgMail) entity).getRecordType();
        } else if (entity instanceof TMsgSms) {
            recordType = ((TMsgSms) entity).getRecordType();
        } else if (entity instanceof TMsgWxCp) {
            recordType = ((TMsgWxCp) entity).getRecordType();
        } else if (entity instanceof TMsgHttp) {
            recordType = ((TMsgHttp) entity).getRecordType();
        } else if (entity instanceof TMsgDing) {
            recordType = ((TMsgDing) entity).getRecordType();
        } else if (entity instanceof TMsgFeishu) {
            recordType = ((TMsgFeishu) entity).getRecordType();
        }
        return recordType != null && recordType == MessageRecordTypeEnum.PUSH;
    }

    private void fillTemplateName(TMsgMail mail) {
        if (mail.getRefTemplateId() == null) return;
        TMsgMail tpl = tMsgMailMapper.selectByPrimaryKey(mail.getRefTemplateId());
        if (tpl != null) {
            mail.setTemplateName(tpl.getTitle() != null ? tpl.getTitle() : tpl.getMsgName());
        }
    }

    private void fillTemplateName(TMsgSms sms) {
        if (sms.getRefTemplateId() == null) return;
        TMsgSms tpl = tMsgSmsMapper.selectByPrimaryKey(sms.getRefTemplateId());
        if (tpl != null) {
            sms.setTemplateName(tpl.getMsgName());
        }
    }

    private void fillTemplateName(TMsgWxCp wxCp) {
        if (wxCp.getRefTemplateId() == null) return;
        TMsgWxCp tpl = tMsgWxCpMapper.selectByPrimaryKey(wxCp.getRefTemplateId());
        if (tpl != null) {
            wxCp.setTemplateName(tpl.getTitle() != null ? tpl.getTitle() : tpl.getMsgName());
        }
    }

    private void fillTemplateName(TMsgHttp http) {
        if (http.getRefTemplateId() == null) return;
        TMsgHttp tpl = tMsgHttpMapper.selectByPrimaryKey(http.getRefTemplateId());
        if (tpl != null) {
            http.setTemplateName(tpl.getMsgName());
        }
    }

    private void fillTemplateName(TMsgDing ding) {
        if (ding.getRefTemplateId() == null) return;
        TMsgDing tpl = tMsgDingMapper.selectByPrimaryKey(ding.getRefTemplateId());
        if (tpl != null) {
            ding.setTemplateName(tpl.getTitle() != null ? tpl.getTitle() : tpl.getMsgName());
        }
    }

    private void fillTemplateName(TMsgFeishu feishu) {
        if (feishu.getRefTemplateId() == null) return;
        TMsgFeishu tpl = tMsgFeishuMapper.selectByPrimaryKey(feishu.getRefTemplateId());
        if (tpl != null) {
            feishu.setTemplateName(tpl.getTitle() != null ? tpl.getTitle() : tpl.getMsgName());
        }
    }

    private void clearMailContentForPush(TMsgMail mail) {
        mail.setTitle(null);
        mail.setCc(null);
        mail.setFiles(null);
        mail.setContent(null);
    }

    private void clearSmsContentForPush(TMsgSms sms) {
        sms.setTemplateId(null);
        sms.setContent(null);
    }

    private void clearWxCpContentForPush(TMsgWxCp wxCp) {
        wxCp.setCpMsgType(null);
        wxCp.setAgentId(null);
        wxCp.setContent(null);
        wxCp.setTitle(null);
        wxCp.setImgUrl(null);
        wxCp.setDescribe(null);
        wxCp.setUrl(null);
        wxCp.setBtnTxt(null);
        wxCp.setRadioType(null);
        wxCp.setWebHook(null);
    }

    private void clearHttpContentForPush(TMsgHttp http) {
        http.setMethod(null);
        http.setUrl(null);
        http.setParams(null);
        http.setHeaders(null);
        http.setCookies(null);
        http.setBody(null);
        http.setBodyType(null);
    }

    private void clearDingContentForPush(TMsgDing ding) {
        ding.setRadioType(null);
        ding.setDingMsgType(null);
        ding.setAgentId(null);
        ding.setWebHook(null);
        ding.setContent(null);
        ding.setTitle(null);
        ding.setImgUrl(null);
        ding.setBtnTxt(null);
        ding.setBtnUrl(null);
        ding.setUrl(null);
    }

    private void clearFeishuContentForPush(TMsgFeishu feishu) {
        feishu.setRadioType(null);
        feishu.setFeishuMsgType(null);
        feishu.setWebHook(null);
        feishu.setContent(null);
        feishu.setTitle(null);
        feishu.setImgUrl(null);
        feishu.setBtnTxt(null);
        feishu.setBtnUrl(null);
        feishu.setUrl(null);
    }
}
