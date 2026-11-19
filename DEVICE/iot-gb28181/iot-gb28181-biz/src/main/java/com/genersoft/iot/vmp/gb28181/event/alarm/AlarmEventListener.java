package com.genersoft.iot.vmp.gb28181.event.alarm;

import com.genersoft.iot.vmp.gb28181.session.SseSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 报警事件监听器.
 *
 * @author liufei
 * @author liufei
 * @since 2021/01/20
 */
@Slf4j
@Component
public class AlarmEventListener implements ApplicationListener<AlarmEvent> {

    @Resource
    private SseSessionManager sseSessionManager;

    @Override
    public void onApplicationEvent(@NotNull AlarmEvent event) {
        if (log.isDebugEnabled()) {
            log.debug("设备报警事件触发, deviceId: {}, {}", event.getAlarmInfo().getDeviceId(), event.getAlarmInfo().getAlarmDescription());
        }
        sseSessionManager.sendForAll("message", event.getAlarmInfo());
    }
}
