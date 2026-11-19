package com.basiclab.iot.transform.core.spi;

import com.basiclab.iot.transform.core.envelope.TransformEnvelope;

/**
 * 将 iot-sink 原始 Kafka 记录归一化为 {@link TransformEnvelope}。
 */
@FunctionalInterface
public interface EnvelopeNormalizer {

    TransformEnvelope normalize(String topic, String key, String valueJson);
}
