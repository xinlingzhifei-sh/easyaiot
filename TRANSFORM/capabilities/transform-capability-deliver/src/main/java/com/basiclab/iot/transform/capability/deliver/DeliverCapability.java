package com.basiclab.iot.transform.capability.deliver;

import com.basiclab.iot.transform.core.envelope.TransformEnvelope;

/**
 * 投递能力：将信封投递到目标渠道/Party。
 */
public interface DeliverCapability {

    void deliver(String targetId, TransformEnvelope envelope) throws Exception;
}
