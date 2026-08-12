package com.basiclab.iot.message.controller;

import com.basiclab.iot.common.domain.AjaxResult;
import com.basiclab.iot.common.domain.TableDataInfo;
import com.basiclab.iot.common.web.controller.BaseController;
import com.basiclab.iot.message.domain.model.vo.MessagePrepareVO;
import com.basiclab.iot.message.security.MessageInternalAccessVerifier;
import com.basiclab.iot.message.service.MessageTemplateService;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 消息模板管理（与消息推送分离，record_type=0）
 *
 * @author reese
 * @email reese
 * @since 2024-07-18
 */
@RestController
@RequestMapping("/message/template")
@Tag(name = "消息模板管理")
public class MessageTemplateController extends BaseController {

    @Autowired
    private MessageTemplateService messageTemplateService;
    @Autowired
    private MessageInternalAccessVerifier messageInternalAccessVerifier;

    @PostMapping("/add")
    @ApiOperation("新增消息模板")
    public AjaxResult add(@RequestBody MessagePrepareVO vo) {
        try {
            return AjaxResult.success(messageTemplateService.add(vo));
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("/update")
    @ApiOperation("更新消息模板")
    public AjaxResult update(@RequestBody MessagePrepareVO vo) {
        try {
            return AjaxResult.success(messageTemplateService.update(vo));
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/delete")
    @ApiOperation("删除消息模板")
    public AjaxResult delete(@RequestParam("msgType") int msgType, @RequestParam("id") String id) {
        return AjaxResult.success(messageTemplateService.delete(msgType, id));
    }

    @GetMapping("/query")
    @ApiOperation("分页查询消息模板")
    public TableDataInfo query(@ModelAttribute MessagePrepareVO vo) {
        startPage();
        List<?> list = messageTemplateService.query(vo);
        return getDataTable(list);
    }

    @GetMapping("/queryByType")
    @ApiOperation("根据消息类型查询模板列表")
    public AjaxResult queryByType(@RequestParam("msgType") Integer msgType) {
        if (msgType == null) {
            return AjaxResult.error("消息类型不能为空");
        }
        try {
            List<Map<String, Object>> templates = messageTemplateService.queryByType(msgType);
            return AjaxResult.success(templates);
        } catch (Exception e) {
            return AjaxResult.error("查询模板列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/get")
    @ApiOperation("根据ID和消息类型获取模板详情")
    public AjaxResult get(
            @RequestHeader(value = MessageInternalAccessVerifier.TOKEN_HEADER, required = false)
            String internalToken,
            @RequestParam("id") String id,
            @RequestParam("msgType") Integer msgType) {
        messageInternalAccessVerifier.verify(internalToken);
        return getTemplate(id, msgType);
    }

    private AjaxResult getTemplate(String id, Integer msgType) {
        if (id == null || id.trim().isEmpty()) {
            return AjaxResult.error("模板ID不能为空");
        }
        if (msgType == null) {
            return AjaxResult.error("消息类型不能为空");
        }
        try {
            Map<String, Object> template = messageTemplateService.getById(id, msgType);
            if (template == null) {
                return AjaxResult.error("模板不存在");
            }
            return AjaxResult.success(template);
        } catch (Exception e) {
            return AjaxResult.error("获取模板详情失败: " + e.getMessage());
        }
    }

    @GetMapping("/queryById")
    @ApiOperation("根据ID和消息类型获取模板详情（兼容旧接口）")
    public AjaxResult queryById(@RequestParam("id") String id, @RequestParam("msgType") Integer msgType) {
        return getTemplate(id, msgType);
    }
}
