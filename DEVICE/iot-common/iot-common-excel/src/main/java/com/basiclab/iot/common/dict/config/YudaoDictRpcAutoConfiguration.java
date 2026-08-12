package com.basiclab.iot.common.dict.config;

import com.basiclab.iot.system.api.dict.DictDataApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 字典用到 Feign 的配置项
 *
 * @author reese
 * @email reese
 */
@AutoConfiguration
@ConditionalOnExpression("'${spring.application.name:}' != 'system-server'")
@EnableFeignClients(clients = DictDataApi.class) // 主要是引入相关的 API 服务
public class YudaoDictRpcAutoConfiguration {
}
