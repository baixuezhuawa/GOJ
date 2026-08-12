package com.gusl.gojjudge.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个测试点的运行上下文。
 *
 * <p>该对象把测试输入、可复用的编译产物和题目运行限制传给语言适配器，
 * 适配器据此生成一次 go-judge 运行请求。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RunContext {

    /** 当前测试点的标准输入内容。 */
    String input;

    /** 编译阶段产生的沙箱文件引用。 */
    ProgramArtifact program;

    /** 题目运行限制，业务单位为毫秒和 KB。 */
    RuntimeLimit limit;
}
