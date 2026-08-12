package com.basiclab.iot.device.enums.device;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Description: 产品协议类型
 * @author reese
 * @email reese
 * @CreateDate: 2024/10/25$ 15:57$
 * @UpdateDate: 2024/10/25$ 15:57$
 */
@Getter
@AllArgsConstructor
public enum ProtocolType {

    /**
     * MQTT协议
     */
    MQTT("MQTT","MQTT"),


    /**
     * COAP协议
     */
    COAP("COAP","COAP"),

    /**
     * MODBUS协议
     */
    MODBUS("MODBUS", "MODBUS"),

    MODBUS_TCP("MODBUS_TCP", "Modbus TCP"),

    /**
     * Modbus RTU over RS-485
     */
    MODBUS_RTU("MODBUS_RTU", "Modbus RTU"),

    /**
     * OPC UA 协议
     */
    OPCUA("OPCUA", "OPC UA"),

    /**
     * HTTP协议
     */
    HTTP("HTTP","HTTP");

    private  String key;
    private  String value;

    /**
     * 工业轮询协议：由 sink 直连采集点位，无需 JS 协议脚本编解码。
     */
    public static boolean isIndustrial(String protocolType) {
        return ProtocolTypeEnum.isIndustrial(protocolType);
    }
}
