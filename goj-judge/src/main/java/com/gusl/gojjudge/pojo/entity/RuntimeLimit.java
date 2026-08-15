package com.gusl.gojjudge.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户程序运行阶段的资源限制。
 *
 * <p>字段单位遵循 GOJ 业务模型：时间为毫秒，内存为 KB；发送给 go-judge 前会由
 * {@code AbstractGoJudgeLanguageAdapter} 转换为纳秒和字节。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeLimit {

    /** 用户程序的 CPU 时间限制，单位为毫秒。 */
    private Integer timeLimit;

    /** 用户程序的内存限制，单位为 KB。 */
    private Integer memoryLimit;

}
