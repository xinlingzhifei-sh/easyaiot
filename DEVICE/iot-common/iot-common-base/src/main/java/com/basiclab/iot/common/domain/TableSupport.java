package com.basiclab.iot.common.domain;

import com.basiclab.iot.common.utils.ServletUtils;

/**
 * 表格数据处理
 * 
 * @author reese
 * @email reese
 */
public class TableSupport
{
    /**
     * 当前记录起始索引
     */
    public static final String PAGE_NUM = "pageNum";

    /**
     * 前端表格默认分页参数名（兼容）
     */
    public static final String PAGE_NO = "pageNo";

    /**
     * 历史分页参数名（兼容）
     */
    public static final String PAGE = "page";

    /**
     * 每页显示记录数
     */
    public static final String PAGE_SIZE = "pageSize";

    /**
     * 排序列
     */
    public static final String ORDER_BY_COLUMN = "orderByColumn";

    /**
     * 排序的方向 "desc" 或者 "asc".
     */
    public static final String IS_ASC = "isAsc";

    /**
     * 分页参数合理化
     */
    public static final String REASONABLE = "reasonable";

    /**
     * 封装分页对象
     */
    public static PageDomain getPageDomain()
    {
        PageDomain pageDomain = new PageDomain();
        Integer pageNum = ServletUtils.getParameterToInt(PAGE_NUM);
        if (pageNum == null) {
            pageNum = ServletUtils.getParameterToInt(PAGE_NO);
        }
        if (pageNum == null) {
            pageNum = ServletUtils.getParameterToInt(PAGE);
        }
        pageDomain.setPageNum(pageNum);
        pageDomain.setPageSize(ServletUtils.getParameterToInt(PAGE_SIZE));
        pageDomain.setOrderByColumn(ServletUtils.getParameter(ORDER_BY_COLUMN));
        pageDomain.setIsAsc(ServletUtils.getParameter(IS_ASC));
        pageDomain.setReasonable(ServletUtils.getParameterToBool(REASONABLE));
        return pageDomain;
    }

    public static PageDomain buildPageRequest()
    {
        return getPageDomain();
    }
}
