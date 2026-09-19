package com.gusl.gojserver.shared.infrastructure.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.gusl.gojserver.shared.infrastructure.config.properties.SysProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan({
        "com.gusl.gojserver.account.infrastructure.persistence",
        "com.gusl.gojserver.profile.infrastructure.persistence",
        "com.gusl.gojserver.problem.infrastructure.persistence",
        "com.gusl.gojserver.submission.infrastructure.persistence",
        "com.gusl.gojserver.contest.infrastructure.persistence",
        "com.gusl.gojserver.judgetask.infrastructure.persistence"
})
public class MybatisPlusConfig {

    /**
     * 配置 MyBatis-Plus 分页拦截器。
     * @return MyBatis-Plus 拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(SysProperties sysProperties) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);

        // 设置每页最大条数
        paginationInterceptor.setMaxLimit(sysProperties.getPagination().getMaxSize());

        interceptor.addInnerInterceptor(paginationInterceptor);

        return interceptor;
    }

}
