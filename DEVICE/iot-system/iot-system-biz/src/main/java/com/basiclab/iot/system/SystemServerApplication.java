package com.basiclab.iot.system;


import com.basiclab.iot.common.annotation.EnableCustomSwagger2;
import com.basiclab.iot.common.annotations.EnableCustomConfig;
import com.basiclab.iot.common.annotations.EnableRyFeignClients;
import com.basiclab.iot.common.config.YudaoOperateLogRpcAutoConfiguration;
import com.basiclab.iot.common.config.YudaoSecurityFeignClientsAutoConfiguration;
import com.basiclab.iot.common.config.YudaoTenantFeignClientsAutoConfiguration;
import com.basiclab.iot.common.dict.config.YudaoDictRpcAutoConfiguration;
import com.basiclab.iot.infra.api.file.FileApi;
import com.basiclab.iot.infra.api.websocket.WebSocketSenderApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.CrossOrigin;

/**
 * SystemServerApplication
 *
 * @author reese
 * @email reese
 */
@Slf4j
@EnableCustomConfig
@EnableCustomSwagger2
@EnableRyFeignClients(clients = {FileApi.class, WebSocketSenderApi.class})
@CrossOrigin(origins = "*", maxAge = 3600)
@SpringBootApplication(
        scanBasePackages = {"com.basiclab.iot"},
        exclude = {
                YudaoSecurityFeignClientsAutoConfiguration.class,
                YudaoTenantFeignClientsAutoConfiguration.class,
                YudaoDictRpcAutoConfiguration.class,
                YudaoOperateLogRpcAutoConfiguration.class
        })
public class SystemServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SystemServerApplication.class, args);
    }

}
