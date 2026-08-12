package com.basiclab.iot.sink.service.tdengine.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.basiclab.iot.sink.dal.mapper.TdEngineMapper;
import com.basiclab.iot.sink.service.tdengine.TdEngineService;
import com.basiclab.iot.tdengine.domain.Fields;
import com.basiclab.iot.tdengine.domain.model.TableDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.regex.Pattern;

/**
 * TdEngineServiceImpl
 *
 * @author reese
 * @email reese
 */

@Slf4j
@Service
@DS("tdengine")
public class TdEngineServiceImpl implements TdEngineService {

    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    @Resource
    private TdEngineMapper tdEngineMapper;

    @Override
    public void insertTableData(TableDTO tableDTO) {
        String tableName = tableDTO != null ? tableDTO.getTableName() : null;
        try {
            if (tableDTO == null) {
                throw new IllegalArgumentException("TDengine tableDTO must not be null");
            }
            validateIdentifier("dataBaseName", tableDTO.getDataBaseName());
            validateIdentifier("tableName", tableName);
            validateIdentifier("superTableName", tableDTO.getSuperTableName());
            validateFields("schemaFieldValues", tableDTO.getSchemaFieldValues());
            validateFields("tagsFieldValues", tableDTO.getTagsFieldValues());
            tdEngineMapper.insertTableData(tableDTO);
            log.debug("[insertTableData][TDEngine数据插入成功，tableName: {}]", tableName);
        } catch (Exception e) {
            log.error("[insertTableData][TDEngine数据插入失败，tableName: {}]", tableName, e);
            throw e;
        }
    }

    private void validateIdentifier(String fieldName, String value) {
        if (value == null || !SQL_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid TDengine " + fieldName + ": " + value);
        }
    }

    private void validateFields(String fieldName, List<Fields> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("TDengine " + fieldName + " must not be empty");
        }
        for (Fields field : fields) {
            if (field == null) {
                throw new IllegalArgumentException("TDengine " + fieldName + " contains a null field");
            }
            validateIdentifier(fieldName + ".fieldName", field.getFieldName());
            if (field.getDataType() == null) {
                throw new IllegalArgumentException("TDengine field dataType is missing: " + field.getFieldName());
            }
        }
    }
}

