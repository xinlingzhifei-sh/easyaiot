package com.basiclab.iot.message.factory;

import com.basiclab.iot.common.domain.AjaxResult;
import com.basiclab.iot.common.domain.TableDataInfo;
import com.basiclab.iot.message.RemoteMessageNotifyQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 消息中心模板/用户查询 Feign 降级
 */
@Slf4j
@Component
public class RemoteMessageNotifyQueryFactory implements FallbackFactory<RemoteMessageNotifyQueryService> {

    @Override
    public RemoteMessageNotifyQueryService create(Throwable cause) {
        log.error("消息中心模板/用户查询调用失败: {}", cause.getMessage());
        return new RemoteMessageNotifyQueryService() {
            @Override
            public AjaxResult getTemplate(String internalToken, String id, Integer msgType) {
                return AjaxResult.error("消息中心不可用");
            }

            @Override
            public TableDataInfo queryUserGroup(String internalToken, String id) {
                TableDataInfo table = new TableDataInfo();
                table.setData(Collections.emptyList());
                return table;
            }

            @Override
            public TableDataInfo queryPreviewUser(String internalToken, String id, Integer msgType) {
                TableDataInfo table = new TableDataInfo();
                table.setData(Collections.emptyList());
                return table;
            }
        };
    }
}
