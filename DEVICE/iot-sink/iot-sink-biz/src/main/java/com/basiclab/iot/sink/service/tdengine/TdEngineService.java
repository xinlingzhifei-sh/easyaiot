package com.basiclab.iot.sink.service.tdengine;

import com.basiclab.iot.tdengine.domain.model.TableDTO;

/**
 * TdEngineService
 *
 * @author liufei
 */

public interface TdEngineService {

    /**
     * 插入表数据
     *
     * @param tableDTO 表数据DTO
     */
    void insertTableData(TableDTO tableDTO);
}

