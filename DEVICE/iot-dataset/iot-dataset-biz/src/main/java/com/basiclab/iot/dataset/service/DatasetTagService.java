package com.basiclab.iot.dataset.service;

import javax.validation.*;
import com.basiclab.iot.common.domain.PageResult;
import com.basiclab.iot.dataset.dal.dataobject.DatasetTagDO;
import com.basiclab.iot.dataset.domain.dataset.vo.DatasetTagPageReqVO;
import com.basiclab.iot.dataset.domain.dataset.vo.DatasetTagSaveReqVO;

import java.util.List;

/**
 * 数据集标签 Service 接口
 *
 * @author reese
 * @email reese
 */
public interface DatasetTagService {

    /**
     * 创建数据集标签
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createDatasetTag(@Valid DatasetTagSaveReqVO createReqVO);

    /**
     * 更新数据集标签
     *
     * @param updateReqVO 更新信息
     */
    void updateDatasetTag(@Valid DatasetTagSaveReqVO updateReqVO);

    /**
     * 删除数据集标签
     *
     * @param id 编号
     */
    void deleteDatasetTag(Long id);

    /**
     * 获得数据集标签
     *
     * @param id 编号
     * @return 数据集标签
     */
    DatasetTagDO getDatasetTag(Long id);

    /**
     * 获得数据集标签分页
     *
     * @param pageReqVO 分页查询
     * @return 数据集标签分页
     */
    PageResult<DatasetTagDO> getDatasetTagPage(DatasetTagPageReqVO pageReqVO);

    /**
     * 按类别名批量补齐数据集标签（已存在则跳过），返回新建数量
     *
     * @param datasetId  数据集 ID
     * @param classNames 类别名列表（有序，如 YOLO classes.txt）
     * @return 新建标签数
     */
    int ensureTagsForDataset(Long datasetId, List<String> classNames);

    /**
     * 获取数据集下全部标签，按 shortcut 升序
     */
    List<DatasetTagDO> listTagsByDatasetId(Long datasetId);

}