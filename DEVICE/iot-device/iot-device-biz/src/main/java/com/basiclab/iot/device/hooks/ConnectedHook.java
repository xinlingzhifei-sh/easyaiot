package com.basiclab.iot.device.hooks;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Map;

/**
 * ConnectedHook
 *
 * @author reese
 * @email reese
 */
@Setter
@Getter
@ToString
public class ConnectedHook extends BaseHook implements Serializable {
    /**
     * 时间戳(秒)
     */
    private Long connected_at;
    /**
     * 客户端申请的心跳保活时间
     */
    private Integer keepalive;


    public ConnectedHook(Map<String, Object> map) {
        super(map);
        this.setConnected_at((Long) map.get("connected_at"));
        this.setKeepalive((Integer) map.get("keepalive"));
    }
}