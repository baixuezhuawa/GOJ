package com.gusl.common.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageResult<T> {

    /** 当前页码 */
    private long current;

    /** 每页数量 */
    private long size;

    /** 总记录数 */
    private long total;

    /** 总页数 */
    private long pages;

    /** 当前页记录 */
    private List<T> records;

    /**
     * 将 MyBatis-Plus 分页对象转换为统一分页响应。
     *
     * @param page MyBatis-Plus 分页对象
     * @return 统一分页响应
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getPages(),
                page.getRecords()
        );
    }

    /** 返回空分页对象 */
    public static <T> PageResult<T> empty(){
        return new PageResult<>(
                0,
                0,
                0,
                0,
                Collections.emptyList()
        );
    }

}
