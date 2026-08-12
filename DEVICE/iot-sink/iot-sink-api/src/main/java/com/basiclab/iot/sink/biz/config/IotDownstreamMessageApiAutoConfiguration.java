package com.basiclab.iot.sink.biz.config;

import com.basiclab.iot.common.service.RedisService;
import com.basiclab.iot.sink.service.DeviceServerIdService;
import com.basiclab.iot.sink.service.impl.DeviceServerIdServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * IotDownstreamMessageApiAutoConfiguration
 *
 * @author reese
 * @email reese
 */

@AutoConfiguration
@Slf4j
public class IotDownstreamMessageApiAutoConfiguration {

    @Bean
    @ConditionalOnBean(RedisService.class)
    @ConditionalOnMissingBean(DeviceServerIdService.class)
    public DeviceServerIdService deviceServerIdService(RedisService redisService) {
        return new DeviceServerIdServiceImpl(redisService);
    }

}
