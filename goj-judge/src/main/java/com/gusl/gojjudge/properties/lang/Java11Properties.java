package com.gusl.gojjudge.properties.lang;

import com.gusl.gojjudge.pojo.entity.CompileLimitInfo;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Java 11 工具链配置。
 *
 * <p>该类绑定 {@code goj.judge.languages.java11}，保存编译器、运行时、环境变量
 * 和编译阶段限制。语言适配器只依赖这个配置类，不直接读取 YAML。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "goj.judge.languages.java11")
public class Java11Properties {

    /** 与提交记录一致的语言编码。 */
    private String code;

    /** 沙箱内 {@code javac} 可执行文件路径。 */
    private String javac;

    /** 沙箱内 {@code java} 可执行文件路径。 */
    private String java;

    /** 沙箱内 {@code jar} 打包工具路径。 */
    private String jar;

    /** 沙箱命令需要的环境变量，例如 Java 11 的 PATH。 */
    private String env;

    /** 沙箱编译后缓存文件名字 */
    private String activeCacheFileName;

    /** Java 编译阶段资源和输出限制。 */
    private CompileLimitInfo compile;
}
