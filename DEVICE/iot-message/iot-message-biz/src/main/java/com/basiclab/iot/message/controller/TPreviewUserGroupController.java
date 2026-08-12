package com.basiclab.iot.message.controller;

import com.basiclab.iot.common.web.controller.BaseController;
import com.basiclab.iot.common.domain.AjaxResult;
import com.basiclab.iot.common.domain.TableDataInfo;
import com.basiclab.iot.message.domain.entity.TPreviewUserGroup;
import com.basiclab.iot.message.security.MessageInternalAccessVerifier;
import com.basiclab.iot.message.service.TPreviewUserGroupService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户组管理控制层controller
 *
 * @author reese
 * @email reese
 * @since 2024-07-17
 *
 */
@RestController
@RequestMapping("/message/preview/user/group")
@Tag(name  ="用户组管理")
public class TPreviewUserGroupController extends BaseController {

    @Autowired
    private TPreviewUserGroupService tPreviewUserGroupService;
    @Autowired
    private MessageInternalAccessVerifier messageInternalAccessVerifier;

    @PostMapping("/add")
    @ApiOperation("新增")
    public AjaxResult add(@RequestBody TPreviewUserGroup tPreviewUserGroup){
        return tPreviewUserGroupService.add(tPreviewUserGroup);
    }

    @PostMapping("/update")
    @ApiOperation("更新")
    public AjaxResult update(@RequestBody TPreviewUserGroup tPreviewUserGroup){
        return AjaxResult.success(tPreviewUserGroupService.update(tPreviewUserGroup));
    }

    @GetMapping("/delete")
    @ApiOperation("删除")
    public AjaxResult delete(String id){
        return AjaxResult.success(tPreviewUserGroupService.delete(id));
    }

    @GetMapping("/query")
    @ApiOperation("查询")
    public TableDataInfo query(
            @RequestHeader(value = MessageInternalAccessVerifier.TOKEN_HEADER, required = false)
            String internalToken,
            @ModelAttribute TPreviewUserGroup tPreviewUserGroup){
        messageInternalAccessVerifier.verify(internalToken);
        startPage();
        List<TPreviewUserGroup> query = tPreviewUserGroupService.query(tPreviewUserGroup);
        return getDataTable(query);
    }

    @GetMapping("/queryByMsgType")
    public AjaxResult queryByMsgType(Integer msgType){
        return AjaxResult.success(tPreviewUserGroupService.queryByMsgType(msgType));
    }
}
