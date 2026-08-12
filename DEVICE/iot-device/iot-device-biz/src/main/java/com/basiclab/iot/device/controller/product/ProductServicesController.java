package com.basiclab.iot.device.controller.product;

import com.basiclab.iot.common.adapter.ExcelUtil;
import com.basiclab.iot.common.domain.AjaxResult;
import com.basiclab.iot.common.domain.R;
import com.basiclab.iot.common.domain.TableDataInfo;
import com.basiclab.iot.common.utils.SecurityUtils;
import com.basiclab.iot.common.web.controller.BaseController;
import com.basiclab.iot.device.domain.device.vo.ProductServiceDetailVO;
import com.basiclab.iot.device.domain.device.vo.ProductServices;
import com.basiclab.iot.device.service.product.ProductServiceThingModelHelper;
import com.basiclab.iot.device.service.product.ProductServicesService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * (productServices)表控制层
 *
 * @author reese
 * @email reese
 */
@RestController
@RequestMapping("/productServices")
public class ProductServicesController extends BaseController {
    /**
     * 服务对象
     */
    @Resource
    private ProductServicesService productServicesService;
    @Resource
    private ProductServiceThingModelHelper productServiceThingModelHelper;

    /**
     * 通过主键查询单条数据
     *
     * @param id 主键
     * @return 单条数据
     */
    @GetMapping("selectOne")
    public ProductServices selectOne(Long id) {
        return productServicesService.selectByPrimaryKey(id);
    }

    /**
     * 通过主键查询单条数据
     *
     * @param id 主键
     * @return 单条数据
     */
    @GetMapping(value = "/selectProductServicesById/{id}")
    public R<?> selectProductServicesById(@PathVariable("id") Long id) {
        return R.ok(productServicesService.selectProductServicesById(id));
    }

    /**
     * 查询产品模型服务列表
     */
    //@PreAuthorize(hasPermi = "link:productServices:list")
    @GetMapping("/list")
    public TableDataInfo list(ProductServices productServices) {
        startPage();
        List<ProductServices> list = productServicesService.selectProductServicesList(productServices);
        return getDataTable(list);
    }

    /**
     * 查询产品模型服务列表
     */
    //@PreAuthorize(hasPermi = "link:productServices:list")
    @GetMapping("/query")
    public AjaxResult query(ProductServices productServices) {
        List<ProductServices> list = productServicesService.selectProductServicesList(productServices);
        return AjaxResult.success(list);
    }

    /**
     * 导出产品模型服务列表
     */
    //@PreAuthorize(hasPermi = "link:productServices:export")
    //@Log(title = "产品模型服务", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ProductServices productServices) throws IOException {
        List<ProductServices> list = productServicesService.selectProductServicesList(productServices);
        ExcelUtil<ProductServices> util = new ExcelUtil<ProductServices>(ProductServices.class);
        util.exportExcel(response, list, "产品模型服务数据");
    }

    /**
     * 查询服务详情（含入参/出参）— 须放在 /{id} 之前
     */
    @GetMapping("/{id}/detail")
    public AjaxResult getDetail(@PathVariable("id") Long id) {
        ProductServiceDetailVO detail = productServiceThingModelHelper.getDetail(id);
        return detail == null ? AjaxResult.error("服务不存在") : AjaxResult.success(detail);
    }

    /**
     * 保存服务及入参/出参（自动维护默认命令）
     */
    @PostMapping("/saveWithParams")
    public AjaxResult saveWithParams(@RequestBody ProductServiceDetailVO detail) {
        try {
            ProductServiceDetailVO saved = productServiceThingModelHelper.saveWithParams(detail);
            return AjaxResult.success("保存成功", saved);
        } catch (IllegalArgumentException ex) {
            return AjaxResult.error(ex.getMessage());
        }
    }

    /**
     * 更新服务及入参/出参
     */
    @PutMapping("/saveWithParams")
    public AjaxResult updateWithParams(@RequestBody ProductServiceDetailVO detail) {
        try {
            if (detail == null || detail.getId() == null) {
                return AjaxResult.error("服务ID不能为空");
            }
            ProductServiceDetailVO saved = productServiceThingModelHelper.saveWithParams(detail);
            return AjaxResult.success("保存成功", saved);
        } catch (IllegalArgumentException ex) {
            return AjaxResult.error(ex.getMessage());
        }
    }

    /**
     * 获取产品模型服务详细信息
     */
    //@PreAuthorize(hasPermi = "link:productServices:query")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id) {
        return AjaxResult.success(productServicesService.selectProductServicesById(id));
    }

    /**
     * 新增产品模型服务
     */
    //@PreAuthorize(hasPermi = "link:productServices:add")
    //@Log(title = "产品模型服务", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ProductServices productServices) {
        productServices.setCreateBy(SecurityUtils.getUsername());
        return toAjax(productServicesService.insertProductServices(productServices));
    }

    /**
     * 修改产品模型服务
     */
    //@PreAuthorize(hasPermi = "link:productServices:edit")
    //@Log(title = "产品模型服务", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ProductServices productServices) {
        productServices.setUpdateBy(SecurityUtils.getUsername());
        return toAjax(productServicesService.updateProductServices(productServices));
    }

    /**
     * 删除产品模型服务（级联删除默认命令与入参/出参）
     */
    //@PreAuthorize(hasPermi = "link:productServices:remove")
    //@Log(title = "产品模型服务", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        productServiceThingModelHelper.deleteCascade(ids);
        return AjaxResult.success();
    }


    /**
     * 根据产品标识和状态获取产品关联服务
     *
     * @param productIdentification
     * @param status
     * @return
     */
    @GetMapping("/selectAllByProductIdentificationAndStatus")
    public R<?> selectAllByProductIdentificationAndStatus(@RequestParam("productIdentification") String productIdentification, @RequestParam("status") String status) {
        return R.ok(productServicesService.selectAllByProductIdentificationAndStatus(productIdentification, status));
    }

    @PostMapping("/selectProductServicesByIdList")
    public R<?> selectProductServicesByIdList(@RequestBody List<Long> serviceIdList) {
        return R.ok(productServicesService.selectProductServicesByIdList(serviceIdList));
    }
}
