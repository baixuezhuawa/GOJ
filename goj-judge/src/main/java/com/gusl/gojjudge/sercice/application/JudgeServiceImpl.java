package com.gusl.gojjudge.sercice.application;

import com.gusl.common.constant.*;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojjudge.exception.*;
import com.gusl.gojjudge.mapper.*;
import com.gusl.gojjudge.pojo.entity.*;
import com.gusl.gojjudge.sercice.engine.HandlerRegistry;
import com.gusl.gojjudge.sercice.engine.JudgeEngine;
import com.gusl.gojjudge.sercice.engine.JudgeSession;
import com.gusl.gojjudge.sercice.submission.SubmissionJudgeContext;
import com.gusl.gojjudge.sercice.submission.SubmissionJudgeHandler;
import com.gusl.gojjudge.sercice.submission.SubmissionResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 测评流程编排服务。
 *
 * <p>该类只负责组织测评业务：读取提交和题目数据、调用语言适配器生成请求、
 * 调用 go-judge 沙箱、比较输出、更新提交状态以及清理沙箱缓存。
 * 用户代码始终在 go-judge 沙箱中运行，Judge 进程本身不直接执行用户代码。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JudgeServiceImpl implements JudgeService {

    /**
     * 提交记录 Mapper，用于读取源码并更新测评状态和结果。
     */
    private final SubmissionMapper submissionMapper;

    /**
     * 管理员验题提交 Mapper，用于读取验题源码并写回独立测评结果。
     */
    private final ProblemReviewSubmissionMapper reviewSubmissionMapper;

    /** 比赛提交记录 Mapper */
    private final ContestSubmissionMapper contestSubmissionMapper;

    /**
     * 普通提交结果写回服务，负责终态幂等更新和派生统计维护。
     */
    private final SubmissionResultService submissionResultService;

    private final HandlerRegistry handlerRegistry;

    private final JudgeEngine judgeEngine;


    /**
     * 执行测评
     * @param message 任务信息
     */
    @Override
    public void judge(JudgeTaskMessage message){
        // 选择对应测评类型的处理器
        SubmissionJudgeHandler handler = handlerRegistry.require(message.getTaskType());

        // 判断这个任务是否已经结束.
        SubmissionJudgeContext context = handler.prepare(message);
        if(context.isFinished()){
            return ;
        }

        JudgeSession cur = new JudgeSession(message, context::write);

        try {
            // 加载材料，推进为等待执行状态
            JudgeExecutionRequest request = context.getExecutionRequest();
            cur.status(JudgingConstant.WAIT).push();

            // 引擎直接操作 cur，最终结果也由引擎 push
            judgeEngine.judge(request, cur);

            // 防止引擎遗漏终态写回，却让 Processor 宣布任务成功
            if (!cur.isFinished()) {
                throw new JudgeSystemException("判题引擎返回时尚未写入终态");
            }
        } catch (Exception exception) {
            // 关闭本次会话，异常继续交给 Processor
            cur.dead();
            throw exception;
        }
    }


    /**
     * 判断测评任务对应的业务记录是否已经进入终态。
     *
     * @param taskType 任务类型
     * @param businessId 业务记录 id
     * @return 是否已经进入终态
     */
    @Override
    public boolean isBusinessTerminal(String taskType, Long businessId) {
        if (JudgeTaskType.SUBMISSION.equals(taskType)) {
            Submission submission = submissionMapper.selectById(businessId);
            return submission != null
                    && JudgingConstant.TERMINAL_STATUSES.contains(submission.getStatus());
        }

        if (JudgeTaskType.PROBLEM_REVIEW.equals(taskType)) {
            ProblemReviewSubmission submission = reviewSubmissionMapper.selectById(businessId);
            return submission != null
                    && JudgingConstant.TERMINAL_STATUSES.contains(submission.getStatus());
        }

        if (JudgeTaskType.CONTEST_SUBMISSION.equals(taskType)) {
            ContestSubmission submission = contestSubmissionMapper.selectById(businessId);
            return submission != null
                    && JudgingConstant.TERMINAL_STATUSES.contains(submission.getStatus());
        }

        throw new IllegalArgumentException("不支持的测评任务类型：" + taskType);
    }


    /**
     * 在测评任务死亡后写入业务记录的系统错误终态。
     */
    @Override
    public void markSystemError(String taskType, Long businessId, String errorMessage) {
        JudgeOutcome outcome = new JudgeOutcome();
        outcome.setCurStatus(SystemConstant.SYSTEM_ERROR);
        outcome.setJudgeMsg(errorMessage);

        // 普通测评
        if (JudgeTaskType.SUBMISSION.equals(taskType)) {
            Submission submission = submissionMapper.selectById(businessId);
            if (submission == null) {
                log.error("普通提交不存在，submissionId={}", businessId);
                return;
            }

            submissionResultService.updateSubmission(submission, outcome);
            return;
        }

        // 验题
        if (JudgeTaskType.PROBLEM_REVIEW.equals(taskType)) {
            ProblemReviewSubmission submission = reviewSubmissionMapper.selectById(businessId);
            if (submission == null) {
                log.error("验题提交不存在，submissionId={}", businessId);
                return;
            }
            // 因为是验题, 不需要更新有关用户做题数据的表
            submissionResultService.updateSubmission(submission, outcome);
            return;
        }

        // 比赛提交
        if (JudgeTaskType.CONTEST_SUBMISSION.equals(taskType)) {
            ContestSubmission submission = contestSubmissionMapper.selectById(businessId);
            if (submission == null) {
                log.error("比赛提交不存在，submissionId={}", businessId);
                return;
            }
            submissionResultService.updateSubmission(submission, outcome);
            return;
        }

        log.error("无法写入系统错误，不支持的任务类型：{}", taskType);
    }

}
