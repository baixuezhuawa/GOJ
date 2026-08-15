package com.gusl.gojjudge.pojo.entity;

import lombok.Data;

/**
 * 测评过程中的输出信息, 作为载体统一更新Submission. 保证了updateSubmission()方法的优雅
 */
@Data
public class JudgeOutcome {

    /** 当前状态 */
    private String curStatus;

    /** 时间/ms */
    private Integer timeMs;

    /** 空间/kb */
    private Integer memoryKb;

    /**
     * 编译信息
     * <p>编译阶段的沙箱的标准和异常输出, 编译错误, 如果编译成功则啥都没有</p>
     */
    private String compilerMsg;

    /**
     * <h>测评信息</h>
     * <p> RE 退出码或者信号 </p>
     * <p> TLE/MLE 沙箱状态 </p>
     * <p> WA 从第几个测试点开始wa的 </p>
     * <p> 系统内部错误摘要 </p>
     */
    private String judgeMsg;

    /** 得分信息, 后续IO模式会用到, 现在只有ACM模式 */
    private Integer score;

}
