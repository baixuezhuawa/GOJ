package com.gusl.gojserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "goj.judge")
public class JudgeProperties {

    private String dataRoot;
}
