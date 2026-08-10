package com.gusl.gojjudge.properties.lang;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * java11 属性类
 */
@Data
@Component
// 不知道可不可以继承这个注解
// TODO 后续不能专门写一个配置类这样读, 看看可以不可以统一放到 Map 里面
@ConfigurationProperties(prefix = "goj.judge.languages.java11")
public class Java11 {

    /**
     * javac 系统路径
     */
    public String javac;

    /**
     * java 系统路径
     */
    public String java;
}
