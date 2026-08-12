package com.basiclab.iot.device.controller.protocol;

import com.basiclab.iot.common.constant.HttpStatus;
import com.basiclab.iot.common.domain.AjaxResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompileXcodeControllerSecurityTest {

    @Test
    void historicalDynamicCompilationRouteAlwaysRejectsExecution() {
        AjaxResult result = new CompileXcodeController().importProductJson();

        assertEquals(HttpStatus.ERROR, result.get(AjaxResult.CODE_TAG));
        assertTrue(String.valueOf(result.get(AjaxResult.MSG_TAG)).contains("永久禁用"));
    }
}
