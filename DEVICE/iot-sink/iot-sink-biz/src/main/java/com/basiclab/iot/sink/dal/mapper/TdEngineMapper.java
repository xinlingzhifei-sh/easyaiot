package com.basiclab.iot.sink.dal.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.basiclab.iot.tdengine.domain.model.TableDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * TdEngineMapper
 *
 * @author reese
 * @email reese
 */

@Mapper
@DS("tdengine")
public interface TdEngineMapper {

    /**
     * 插入表数据
     *
     * @param tableDTO 表数据DTO
     */
    @InterceptorIgnore(tenantLine = "true")
    void insertTableData(TableDTO tableDTO);
}

