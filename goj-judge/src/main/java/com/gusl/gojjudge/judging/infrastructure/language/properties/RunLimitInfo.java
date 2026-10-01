package com.gusl.gojjudge.judging.infrastructure.language.properties;

import lombok.Data;

/**
 * 运行阶段限制，补充题目时间和内存限制以外的沙箱参数。
 */
@Data
public class RunLimitInfo {

    /** 墙钟时间相对 CPU 时间的放大倍数。 */
    private Integer clockLimitMultiplier;

    /** 运行阶段栈空间上限，单位为 KB。 */
    private Integer stackLimitKb;

    /** 运行阶段允许创建的进程数量上限。 */
    private Integer procLimit;

    /** 标准输出上限，单位为字节。 */
    private Integer stdoutLimitBytes;

    /** 标准错误输出上限，单位为字节。 */
    private Integer stderrLimitBytes;

}
