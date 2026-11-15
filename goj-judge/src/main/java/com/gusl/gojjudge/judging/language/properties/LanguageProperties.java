package com.gusl.gojjudge.judging.language.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 语言配置集合，统一绑定测评端支持的语言。
 */
@Data
@Component
@ConfigurationProperties(prefix = "goj.judge")
public class LanguageProperties {

    /** 每项配置创建一个独立的语言适配器。 */
    private List<LanguageConfig> languages = new ArrayList<>();
}
