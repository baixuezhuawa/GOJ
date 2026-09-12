package com.gusl.gojjudge.pojo.entity;

import com.alibaba.fastjson2.JSONObject;
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
    private String input;

    /** 时间限制, 对应cpuLimit*/
    private Long timeLimitMs;

    /** 空间限制 */
    private Long memoryLimitKb;

    /** 可执行文件: fileId/content */
    private JSONObject activeFile;
}
