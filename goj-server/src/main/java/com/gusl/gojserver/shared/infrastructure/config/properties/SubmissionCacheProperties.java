package com.gusl.gojserver.shared.infrastructure.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 提交缓存配置，控制 Canal 消费和结果缓存的有效期。 */
@Data
@Component
@ConfigurationProperties(prefix = "goj.submission-cache")
public class SubmissionCacheProperties {

    private boolean enabled = false;
    private String host = "127.0.0.1";
    private int port = 11111;
    private String destination = "goj";
    private String username = "";
    private String password = "";
    private String database = "goj";
    private int batchSize = 100;
    private long retryMillis = 3000;
    private long activeTtlSeconds = 30;
    private long terminalTtlSeconds = 1200;
    private long terminalJitterSeconds = 120;
    /** 即使 Canal 的上游连接静默失效，也不无限返回旧终态。 */
    private long verifyAfterSeconds = 30;
    private long maxLagSeconds = 30;
}
