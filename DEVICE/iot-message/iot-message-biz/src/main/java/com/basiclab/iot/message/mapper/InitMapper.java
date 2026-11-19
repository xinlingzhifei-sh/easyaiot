package com.basiclab.iot.message.mapper;

/**
 * <pre>
 * 初始化数据库
 * </pre>
 *
 * @author liufei
 * @since 2019/5/9.
 */
public interface InitMapper {
    /**
     * 初始化创建所有表
     *
     * @return
     */
    int createAllTables();
}
