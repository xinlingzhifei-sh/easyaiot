package com.genersoft.iot.vmp.storager.dao;

import org.apache.ibatis.annotations.Param;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloudRecordServiceMapperTest {

    @Test
    void queryRecordListForDeleteNamesEveryMyBatisParameter() throws NoSuchMethodException {
        Method method = CloudRecordServiceMapper.class.getMethod(
                "queryRecordListForDelete", Long.class, String.class);

        List<String> parameterNames = Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getAnnotation(Param.class))
                .map(annotation -> annotation == null ? null : annotation.value())
                .toList();

        assertEquals(List.of("endTimeStamp", "mediaServerId"), parameterNames);
    }
}
