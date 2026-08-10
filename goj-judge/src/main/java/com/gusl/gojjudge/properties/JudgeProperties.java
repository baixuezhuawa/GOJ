package com.gusl.gojjudge.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "goj.judge")
public class JudgeProperties {
    /**
     * 测试数据根目录
     */
    private String dataRoot;

    /**
     * 沙箱请求地址
     */
    private String sandboxBaseUrl;
}
