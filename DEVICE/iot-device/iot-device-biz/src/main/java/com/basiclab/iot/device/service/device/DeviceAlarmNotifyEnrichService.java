package com.basiclab.iot.device.service.device;

import cn.hutool.core.util.StrUtil;
import com.basiclab.iot.common.domain.AjaxResult;
import com.basiclab.iot.common.domain.TableDataInfo;
import com.basiclab.iot.message.RemoteMessageNotifyQueryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设备告警策略：渠道 enrich + 从消息模板提取通知人（对齐算法任务 VIDEO 逻辑）
 */
@Slf4j
@Service
public class DeviceAlarmNotifyEnrichService {

    private static final Set<String> USERLESS_METHODS = Set.of("http", "webhook");

    private static final Map<String, Integer> METHOD_TO_MSG_TYPE = Map.ofEntries(
            Map.entry("sms", 1),
            Map.entry("email", 3),
            Map.entry("mail", 3),
            Map.entry("wxcp", 4),
            Map.entry("wechat", 4),
            Map.entry("weixin", 4),
            Map.entry("http", 5),
            Map.entry("webhook", 5),
            Map.entry("ding", 6),
            Map.entry("dingtalk", 6),
            Map.entry("feishu", 7),
            Map.entry("lark", 7)
    );

    @Resource
    private RemoteMessageNotifyQueryService remoteMessageNotifyQueryService;
    @Resource
    private ObjectMapper objectMapper;
    @Value("${IOT_MESSAGE_INTERNAL_TOKEN:}")
    private String messageInternalToken;

    public static class EnrichResult {
        private final String channelsJson;
        private final String notifyUsersJson;
        private final String notifyMethodsJson;

        public EnrichResult(String channelsJson, String notifyUsersJson, String notifyMethodsJson) {
            this.channelsJson = channelsJson;
            this.notifyUsersJson = notifyUsersJson;
            this.notifyMethodsJson = notifyMethodsJson;
        }

        public String getChannelsJson() {
            return channelsJson;
        }

        public String getNotifyUsersJson() {
            return notifyUsersJson;
        }

        public String getNotifyMethodsJson() {
            return notifyMethodsJson;
        }
    }

    public EnrichResult enrich(String channelsJson, String notifyMethodsJson) {
        List<Map<String, Object>> channels = parseListOfMap(channelsJson);
        if (channels.isEmpty() && StrUtil.isNotBlank(notifyMethodsJson)) {
            List<String> methods = parseListOfString(notifyMethodsJson);
            for (String method : methods) {
                if (StrUtil.isBlank(method)) {
                    continue;
                }
                Map<String, Object> ch = new LinkedHashMap<>();
                ch.put("method", method);
                channels.add(ch);
            }
        }

        List<Map<String, Object>> enrichedChannels = enrichChannelsUserlessFlags(channels);
        List<Map<String, Object>> notifyUsers = extractNotifyUsersFromTemplates(enrichedChannels);

        List<String> methods = new ArrayList<>();
        for (Map<String, Object> ch : enrichedChannels) {
            Object method = ch.get("method");
            if (method != null && StrUtil.isNotBlank(String.valueOf(method))) {
                String m = String.valueOf(method).toLowerCase();
                if (!methods.contains(m)) {
                    methods.add(m);
                }
            }
        }

        try {
            return new EnrichResult(
                    objectMapper.writeValueAsString(enrichedChannels),
                    objectMapper.writeValueAsString(notifyUsers),
                    objectMapper.writeValueAsString(methods)
            );
        } catch (Exception e) {
            log.error("[DeviceAlarmNotifyEnrich] 序列化失败", e);
            return new EnrichResult(
                    StrUtil.blankToDefault(channelsJson, "[]"),
                    "[]",
                    StrUtil.blankToDefault(notifyMethodsJson, "[]")
            );
        }
    }

    private List<Map<String, Object>> enrichChannelsUserlessFlags(List<Map<String, Object>> channels) {
        if (channels == null || channels.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map<String, Object> channel : channels) {
            Map<String, Object> ch = new LinkedHashMap<>(channel);
            String method = String.valueOf(ch.getOrDefault("method", "")).toLowerCase();
            if (isUserlessChannel(ch)) {
                ch.put("userless", true);
                enriched.add(ch);
                continue;
            }
            Object templateId = ch.get("template_id");
            if (templateId != null && StrUtil.isNotBlank(String.valueOf(templateId))) {
                Map<String, Object> meta = fetchTemplateMeta(method, String.valueOf(templateId));
                if (meta != null && isRobotTemplate(meta)) {
                    ch.put("userless", true);
                }
            }
            enriched.add(ch);
        }
        return enriched;
    }

    private boolean isUserlessChannel(Map<String, Object> channel) {
        if (channel == null) {
            return false;
        }
        if (Boolean.TRUE.equals(channel.get("userless"))) {
            return true;
        }
        String method = String.valueOf(channel.getOrDefault("method", "")).toLowerCase();
        return USERLESS_METHODS.contains(method);
    }

    private boolean isRobotTemplate(Map<String, Object> meta) {
        Object radioType = meta.get("radioType");
        if (radioType != null && "群机器人消息".equals(String.valueOf(radioType))) {
            return true;
        }
        Object webHook = meta.get("webHook");
        return webHook != null && StrUtil.isNotBlank(String.valueOf(webHook));
    }

    private List<Map<String, Object>> extractNotifyUsersFromTemplates(List<Map<String, Object>> channels) {
        Map<String, Map<String, Object>> allUsers = new LinkedHashMap<>();
        if (channels == null || channels.isEmpty()) {
            return Collections.emptyList();
        }
        for (Map<String, Object> channel : channels) {
            if (isUserlessChannel(channel)) {
                continue;
            }
            String method = String.valueOf(channel.getOrDefault("method", "")).toLowerCase();
            Object templateIdObj = channel.get("template_id");
            if (templateIdObj == null || StrUtil.isBlank(String.valueOf(templateIdObj))) {
                continue;
            }
            Integer msgType = METHOD_TO_MSG_TYPE.get(method);
            if (msgType == null) {
                continue;
            }
            Map<String, Object> template = fetchTemplateMeta(method, String.valueOf(templateIdObj));
            if (template == null) {
                continue;
            }
            Object userGroupId = template.get("userGroupId");
            if (userGroupId == null) {
                userGroupId = template.get("user_group_id");
            }
            if (userGroupId == null || StrUtil.isBlank(String.valueOf(userGroupId))) {
                continue;
            }
            collectUsersFromGroup(String.valueOf(userGroupId), msgType, allUsers);
        }
        return new ArrayList<>(allUsers.values());
    }

    private void collectUsersFromGroup(String userGroupId, Integer msgType,
                                       Map<String, Map<String, Object>> allUsers) {
        try {
            TableDataInfo table = remoteMessageNotifyQueryService.queryUserGroup(
                    messageInternalToken, userGroupId);
            List<?> rows = table == null ? null : table.getData();
            if (rows == null || rows.isEmpty()) {
                return;
            }
            Object first = rows.get(0);
            Map<String, Object> group = toMap(first);
            if (group == null) {
                return;
            }
            Object tPreviewUsers = group.get("tPreviewUsers");
            if (tPreviewUsers == null) {
                tPreviewUsers = group.get("t_preview_users");
            }
            if (tPreviewUsers instanceof List && !((List<?>) tPreviewUsers).isEmpty()) {
                for (Object userObj : (List<?>) tPreviewUsers) {
                    mergeUser(toMap(userObj), msgType, allUsers);
                }
                return;
            }
            Object previewUserIds = group.get("previewUserId");
            if (previewUserIds == null) {
                previewUserIds = group.get("preview_user_id");
            }
            if (previewUserIds == null || StrUtil.isBlank(String.valueOf(previewUserIds))) {
                return;
            }
            String[] ids = String.valueOf(previewUserIds).split(",");
            for (String id : ids) {
                if (StrUtil.isBlank(id)) {
                    continue;
                }
                TableDataInfo userTable = remoteMessageNotifyQueryService.queryPreviewUser(
                        messageInternalToken, id.trim(), msgType);
                List<?> userRows = userTable == null ? null : userTable.getData();
                if (userRows == null || userRows.isEmpty()) {
                    continue;
                }
                mergeUser(toMap(userRows.get(0)), msgType, allUsers);
            }
        } catch (Exception e) {
            log.warn("[DeviceAlarmNotifyEnrich] 解析用户组失败 groupId={}", userGroupId, e);
        }
    }

    private void mergeUser(Map<String, Object> userDetail, Integer defaultMsgType,
                           Map<String, Map<String, Object>> allUsers) {
        if (userDetail == null || userDetail.isEmpty()) {
            return;
        }
        Object id = userDetail.get("id");
        if (id == null || StrUtil.isBlank(String.valueOf(id))) {
            return;
        }
        Integer userMsgType = defaultMsgType;
        Object rawMsgType = userDetail.get("msgType");
        if (rawMsgType instanceof Number) {
            userMsgType = ((Number) rawMsgType).intValue();
        }
        Map<String, Object> userInfo = allUsers.computeIfAbsent(String.valueOf(id), k -> new LinkedHashMap<>());
        userInfo.put("id", id);
        userInfo.put("msgType", userMsgType);
        Object previewUser = userDetail.get("previewUser");
        if (previewUser == null) {
            previewUser = userDetail.get("preview_user");
        }
        if (previewUser != null) {
            userInfo.put("previewUser", previewUser);
            fillContactByMsgType(userInfo, userMsgType, String.valueOf(previewUser));
        }
        if (userDetail.get("name") != null) {
            userInfo.put("name", userDetail.get("name"));
        }
    }

    private void fillContactByMsgType(Map<String, Object> userInfo, Integer msgType, String previewUser) {
        if (msgType == null || StrUtil.isBlank(previewUser)) {
            return;
        }
        switch (msgType) {
            case 1:
                userInfo.put("phone", previewUser);
                userInfo.put("mobile", previewUser);
                break;
            case 3:
                userInfo.put("email", previewUser);
                userInfo.put("mail", previewUser);
                break;
            case 4:
                userInfo.put("wxcp_userid", previewUser);
                userInfo.put("wechat_userid", previewUser);
                break;
            case 6:
                userInfo.put("ding_userid", previewUser);
                userInfo.put("dingtalk_userid", previewUser);
                break;
            case 7:
                userInfo.put("feishu_userid", previewUser);
                userInfo.put("lark_userid", previewUser);
                break;
            default:
                break;
        }
    }

    private Map<String, Object> fetchTemplateMeta(String method, String templateId) {
        Integer msgType = METHOD_TO_MSG_TYPE.get(method);
        if (msgType == null || StrUtil.isBlank(templateId)) {
            return null;
        }
        try {
            AjaxResult result = remoteMessageNotifyQueryService.getTemplate(
                    messageInternalToken, templateId, msgType);
            if (result == null) {
                return null;
            }
            Object code = result.get(AjaxResult.CODE_TAG);
            if (code instanceof Number && ((Number) code).intValue() != 0
                    && ((Number) code).intValue() != 200) {
                return null;
            }
            Object data = result.get(AjaxResult.DATA_TAG);
            return toMap(data);
        } catch (Exception e) {
            log.warn("[DeviceAlarmNotifyEnrich] 获取模板失败 method={} templateId={}", method, templateId, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) obj);
        }
        try {
            return objectMapper.convertValue(obj, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private List<Map<String, Object>> parseListOfMap(String json) {
        if (StrUtil.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> list = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {});
            return list == null ? new ArrayList<>() : new ArrayList<>(list);
        } catch (Exception e) {
            log.warn("[DeviceAlarmNotifyEnrich] 解析 channels JSON 失败: {}", json);
            return new ArrayList<>();
        }
    }

    private List<String> parseListOfString(String json) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return list == null ? Collections.emptyList() : list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public boolean hasNotifiableConfig(String channelsJson, String notifyUsersJson) {
        List<Map<String, Object>> channels = parseListOfMap(channelsJson);
        List<Map<String, Object>> users = parseListOfMap(notifyUsersJson);
        if (!users.isEmpty()) {
            return true;
        }
        for (Map<String, Object> ch : channels) {
            Object templateId = ch.get("template_id");
            if (templateId != null && StrUtil.isNotBlank(String.valueOf(templateId))) {
                return true;
            }
        }
        return false;
    }
}
