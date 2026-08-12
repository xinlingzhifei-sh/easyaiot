package com.basiclab.iot.device.controller.protocol;

import com.basiclab.iot.common.domain.AjaxResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CompileXcodeController
 *
 * @author reese
 * @email reese
 */
@RestController
@RequestMapping("/protocolCompileXcode")
public class CompileXcodeController {

    /**
     * 兼容保留旧路由，但永久禁用服务端动态 Java 编译执行。
     *
     * @return 禁用提示
     */
    @PostMapping("/dynamicallyXcode")
    public AjaxResult importProductJson() {
        return AjaxResult.error("动态 Java 编译接口已因安全原因永久禁用");
    }

}
