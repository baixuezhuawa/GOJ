package com.gusl.gojjudge.pojo.entity;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 单种语言编译阶段的资源和输出限制。
 *
 * <p>配置文件中的时间使用毫秒、内存和栈使用 KB、输出上限使用字节；
 * 发送到 go-judge 时由语言适配器完成单位转换。</p>
 */
@Data
public class CompileLimitInfo {

    /** 编译进程 CPU 时间上限，单位为毫秒。 */
    private Integer cpuLimitMs;

    /** 编译进程墙钟时间上限，单位为毫秒。 */
    private Integer realCpuLimitMs;

    /** 编译进程内存上限，单位为 KB。 */
    private Integer memoryLimitKb;

    /** 编译进程栈空间上限，单位为 KB。 */
    private Integer stackLimitKb;

    /** 编译阶段允许创建的进程数量上限。 */
    private Integer procLimit;

    /** 编译阶段标准输出上限，单位为字节。 */
    private Integer stdoutLimitBytes;

    /** 编译阶段标准错误输出上限，单位为字节。 */
    private Integer stderrLimitBytes;
}
