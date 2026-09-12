package com.gusl.gojserver.service.support;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gusl.common.common.BaseException;
import com.gusl.common.common.PageQuery;
import com.gusl.gojserver.config.properties.SysProperties;
import com.gusl.gojserver.config.properties.entity.PaginationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 分页对象工厂，负责统一设置分页默认值和校验分页参数。
 */
@Component
@RequiredArgsConstructor
public class PageFactory {

    private final SysProperties sysProperties;

    /**
     * 根据请求参数创建 MyBatis-Plus 分页对象。
     *
     * @param query 分页请求参数
     * @return MyBatis-Plus 分页对象
     */
    public <T> Page<T> create(PageQuery query, OrderItem... item) {
        // 获取系统统一分页配置
        PaginationProperties properties = sysProperties.getPagination();

        // 当前端没有传递分页参数时使用默认值
        long current = query.getPage() == null ? properties.getDefaultPage() : query.getPage();

        long size = query.getSize() == null ? properties.getDefaultSize() : query.getSize();

        // 校验分页参数的最小值，避免负数关闭分页
        if (current < 1) {
            throw new BaseException("页码必须大于 0");
        }
        if (size < 1) {
            throw new BaseException("每页数量必须大于 0");
        }

        Page<T> page = new Page<>(current, size);

        if(item != null){
            for(OrderItem oi : item){
                page.addOrder(oi);
            }
        }

        return page;
    }
}