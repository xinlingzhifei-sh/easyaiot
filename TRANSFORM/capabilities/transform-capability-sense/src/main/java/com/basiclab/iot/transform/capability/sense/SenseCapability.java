package com.basiclab.iot.transform.capability.sense;

import com.basiclab.iot.transform.core.sense.NodeSenseSnapshot;

/**
 * 自感知能力：采集本实例负载、Group 成员、lag，驱动自适应。
 */
public interface SenseCapability {

    NodeSenseSnapshot sense();

    /**
     * 根据快照给出建议：KEEP / SCALE_HINT / DEGRADE_PARTY
     */
    String adapt(NodeSenseSnapshot snapshot);
}
