package com.basiclab.iot.device.controller.protocol;

import com.basiclab.iot.common.constant.HttpStatus;
import com.basiclab.iot.common.domain.AjaxResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompileXcodeControllerTest {

    @Test
    void legacyEndpointCannotCompileOrExecuteCallerSuppliedJava() {
        AjaxResult result = new CompileXcodeController().importProductJson();

        assertEquals(HttpStatus.ERROR, result.get(AjaxResult.CODE_TAG));
        assertEquals("动态 Java 编译接口已因安全原因永久禁用",
                result.get(AjaxResult.MSG_TAG));
    }
}
