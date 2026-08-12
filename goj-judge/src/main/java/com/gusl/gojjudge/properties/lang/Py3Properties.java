package com.gusl.gojjudge.properties.lang;

import com.gusl.gojjudge.pojo.entity.CompileLimitInfo;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "goj.judge.languages.py3")
public class Py3Properties {

    /** 与提交记录一致的语言编码。 */
    private String code;

    /** 沙箱命令需要的环境变量，例如 Java 11 的 PATH。 */
    private String env;

    private String python;

    /** 沙箱编译后缓存文件名字 */
    private String activeFileName;

    /** Java 编译阶段资源和输出限制。 */
    private CompileLimitInfo compile;
}
