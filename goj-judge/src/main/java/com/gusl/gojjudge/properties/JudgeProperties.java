package com.gusl.gojjudge.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Judge 模块的公共配置。
 *
 * <p>配置前缀为 {@code goj.judge}，其中测试数据目录由 Judge 本地读取，
 * 沙箱地址用于 Feign 调用远程 go-judge 服务。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "goj.judge")
public class JudgeProperties {
    /**
     * 测试数据根目录，目录内按 {@code p{problemId}/test{index}} 组织文件。
     */
    private String dataRoot;

    /**
     * go-judge 沙箱请求地址，例如 {@code http://127.0.0.1:5050}。
     */
    private String sandboxBaseUrl;
}
