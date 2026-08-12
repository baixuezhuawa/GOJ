package com.gusl.gojjudge.pojo.entity;

import com.gusl.common.constant.JudgingConstant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 测试点执行结果汇总。
 *
 * <p>{@code time} 使用 go-judge 返回的纳秒，{@code memory} 使用 go-judge 返回的字节。
 * JudgeService 在最终落库时应根据提交表字段约定转换为毫秒和 KB。</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TestResult {

    /** 所有已处理测试点均通过时为 true。 */
    private boolean accepted;

    /** 测评状态, 比如如果是 TLE MLE RE */
    private String status;

    /** 已处理测试点中的最大耗时，单位为纳秒。 */
    private long time;

    /** 已处理测试点中的最大内存占用，单位为字节。 */
    private long memory;


    /**
     * 创建通过结果。
     *
     * @param maxTime 已处理测试点最大耗时，单位为纳秒
     * @param maxMemory 已处理测试点最大内存，单位为字节
     * @return 通过结果
     */
    public static TestResult accepted(long maxTime, long maxMemory){
        return new TestResult(true, JudgingConstant.ACCEPTED, maxTime, maxMemory);
    }

    /**
     * 创建未通过结果。
     *
     * @param time 失败测试点耗时，单位为纳秒
     * @param memory 失败测试点内存，单位为字节
     * @return 未通过结果
     */
    public static TestResult unaccepted(String status, long time, long memory){
        return new TestResult(false, status, time, memory);
    }

    /**
     * 创建无法获得可靠沙箱指标的错误结果。
     *
     * @return 时间和内存均为 0 的未通过结果
     */
    public static TestResult error(String status){
        return new TestResult(false, status, 0, 0);
    }
}
