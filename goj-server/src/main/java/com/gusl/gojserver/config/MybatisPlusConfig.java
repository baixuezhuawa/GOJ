package com.gusl.gojserver.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.gusl.gojserver.mapper")
public class MybatisPlusConfig {
    
}