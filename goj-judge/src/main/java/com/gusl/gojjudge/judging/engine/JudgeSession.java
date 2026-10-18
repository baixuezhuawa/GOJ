package com.gusl.gojjudge.judging.engine;

import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.constant.SystemConstant;
import com.gusl.common.pojo.entity.JudgeTaskMessage;
import com.gusl.gojjudge.judging.exception.JudgeSystemException;
import com.gusl.gojjudge.judging.model.JudgeOutcome;
import com.gusl.gojjudge.judging.compiler.CompileResult;
import com.gusl.gojjudge.judging.language.AbstractLanguageAdapter;
import java.util.function.BiFunction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 本次判题会话，保存执行材料、编译产物及业务回写入口。 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JudgeSession {

    private JudgeTaskMessage task;

    /** Service 准备的题目、代码和测试数据引用。 */
    private JudgeExecutionRequest request;

    /** 本次使用的语言配置与编译产物，结束时由 Engine 清理。 */
    private AbstractLanguageAdapter adapter;
    private CompileResult compileResult;
    private AbstractLanguageAdapter checkerAdapter;
    private CompileResult checkerCompileResult;

    private JudgeOutcome pending;

    private BiFunction<JudgeTaskMessage, JudgeOutcome, Boolean> writer;

    /** 本次会话是否已经结束，包括正常完成和异常中止。 */
    private boolean closed;

    /** 是否已经成功写入正常判题终态。 */
    private boolean finished;


    /** 绑定提交类型对应的业务回写方法。 */
    public JudgeSession(JudgeTaskMessage task, BiFunction<JudgeTaskMessage, JudgeOutcome, Boolean> writer) {
        this.task = task;
        this.writer = writer;
        pending = new JudgeOutcome();
    }


    /** 设置待写回的状态。 */
    public JudgeSession status(String status) {
        requireOpen();
        pending.setCurStatus(status);
        return this;
    }

    /** 设置待写回的编译信息。 */
    public JudgeSession compilerMessage(String message) {
        requireOpen();
        pending.setCompilerMsg(message);
        return this;
    }

    /** 设置待写回的测评说明。 */
    public JudgeSession judgeMessage(String message) {
        requireOpen();
        pending.setJudgeMsg(message);
        return this;
    }

    /** 设置待写回的耗时和内存，单位为毫秒和 KB。 */
    public JudgeSession usage(Integer timeMs, Integer memoryKb) {
        requireOpen();
        pending.setTimeMs(timeMs);
        pending.setMemoryKb(memoryKb);
        return this;
    }

    /** 设置待写回的得分。 */
    public JudgeSession score(Integer score) {
        requireOpen();
        pending.setScore(score);
        return this;
    }


    /** 写回当前数据，正常终态写回成功后关闭会话。 */
    public JudgeSession push(){
        requireOpen();

        if (pending.getCurStatus() == null) {
            throw new IllegalStateException("测评状态尚未设置");
        }

        // 系统错误终态由任务状态服务决定，避免提前终止重试
        if (SystemConstant.SYSTEM_ERROR.equals(pending.getCurStatus())) {
            throw new IllegalStateException("系统错误终态应由任务状态服务写入");
        }

        // 写回失败必须向上传递，不能假装已经保存成功
        if (!Boolean.TRUE.equals(writer.apply(task, pending))) {
            throw new JudgeSystemException("当前任务的测评状态写回被拒绝");
        }

        // 写回成功之后，才能改变本地完成标记
        if (JudgingConstant.TERMINAL_STATUSES.contains(pending.getCurStatus())) {
            finished = true;
            closed = true;
        }

        return this;
    }


    /**
     * 结束本次异常执行，不写入提交终态，也不决定是否重试。
     */
    public void dead() {
        closed = true;
    }

    /** 检查本次会话是否仍允许修改。 */
    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("本次判题会话已经结束");
        }
    }
}
