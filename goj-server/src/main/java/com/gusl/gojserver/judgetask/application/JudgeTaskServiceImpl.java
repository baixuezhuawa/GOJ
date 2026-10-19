package com.gusl.gojserver.judgetask.application;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.common.common.BaseException;
import com.gusl.common.common.PageQuery;
import com.gusl.common.common.PageResult;
import com.gusl.common.constant.JudgeTaskStatus;
import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.constant.SystemConstant;
import com.gusl.common.pojo.entity.*;
import com.gusl.gojserver.contest.infrastructure.persistence.ContestMapper;
import com.gusl.gojserver.judgetask.infrastructure.persistence.JudgeTaskMapper;
import com.gusl.gojserver.problem.infrastructure.persistence.ProblemMapper;
import com.gusl.gojserver.submission.infrastructure.persistence.SubmissionMapper;
import com.gusl.gojserver.account.infrastructure.persistence.UserMapper;
import com.gusl.gojserver.account.domain.model.User;
import com.gusl.gojserver.judgetask.application.vo.JudgeTaskDetailVo;
import com.gusl.gojserver.judgetask.application.vo.JudgeTaskListVo;
import com.gusl.gojserver.shared.application.pagination.PageFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * 测评任务管理服务，提供任务查询和死亡任务版本化重测能力。
 */
@Service
@RequiredArgsConstructor
public class JudgeTaskServiceImpl
        extends ServiceImpl<JudgeTaskMapper, JudgeTask>
        implements JudgeTaskService {

    private final PageFactory pageFactory;

    private final JudgeTaskMapper judgeTaskMapper;

    private final SubmissionMapper submissionMapper;

    private final UserMapper userMapper;
    private final ProblemMapper problemMapper;
    private final ContestMapper contestMapper;


    /**
     * 根据状态获取测评任务列表。
     *
     * @param status 任务状态
     * @param pageQuery 分页参数
     * @return 测评任务列表
     */
    @Override
    public PageResult<JudgeTaskListVo> getListByStatus(String status, PageQuery pageQuery) {
        Page<JudgeTask> page = pageFactory.create(pageQuery);

        Page<JudgeTask> judgeTaskPage = judgeTaskMapper.selectPage(
                page,
                Wrappers.<JudgeTask>lambdaQuery()
                        .eq(status != null, JudgeTask::getStatus, status)
                        .orderByDesc(JudgeTask::getId)
        );

        IPage<JudgeTaskListVo> convert = judgeTaskPage.convert(judgeTask -> {
            JudgeTaskListVo vo = new JudgeTaskListVo();

            vo.setTaskId(judgeTask.getId());
            vo.setSubmissionId(judgeTask.getBusinessId());
            vo.setAttemptCount(judgeTask.getAttemptCount());
            vo.setTaskType(judgeTask.getTaskType());
            vo.setTaskVersion(judgeTask.getTaskVersion());
            vo.setStatus(judgeTask.getStatus());
            vo.setLastError(judgeTask.getLastError());
            vo.setCreateTime(judgeTask.getCreateTime());
            vo.setUpdateTime(judgeTask.getUpdateTime());

            return vo;
        });

        return PageResult.of(convert);
    }

    /**
     * 保留原死亡任务并创建更高版本的重测任务。
     *
     * @param taskId 死亡任务id
     * @return 新创建的任务 id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long retryDeadTask(Long taskId) {
        // 锁定旧任务，避免管理员并发点击时创建两个相同版本。
        JudgeTask deadTask = judgeTaskMapper.selectOne(
                Wrappers.<JudgeTask>lambdaQuery()
                        .eq(JudgeTask::getId, taskId)
                        .last("FOR UPDATE")
        );

        if (deadTask == null || !JudgeTaskStatus.DEAD.equals(deadTask.getStatus())) {
            throw new BaseException("该任务不存在或已经不是死亡状态：" + taskId);
        }

        // 只允许对当前业务记录的最新任务执行重测。
        JudgeTask latestTask = judgeTaskMapper.selectOne(
                Wrappers.<JudgeTask>lambdaQuery()
                        .eq(JudgeTask::getTaskType, deadTask.getTaskType())
                        .eq(JudgeTask::getBusinessId, deadTask.getBusinessId())
                        .orderByDesc(JudgeTask::getTaskVersion)
                        .last("LIMIT 1")
        );

        if (latestTask == null || !latestTask.getId().equals(deadTask.getId())) {
            throw new BaseException("该死亡任务已经存在后续重测版本：" + taskId);
        }

        // 恢复业务记录；SYSTEM_ERROR 不进入个人派生统计，因此无需执行统计回滚。
        resetSubmissionForRejudge(deadTask.getBusinessId(), deadTask.getTaskVersion() + 1);

        JudgeTask newTask = JudgeTask.builder()
                .taskType(deadTask.getTaskType())
                .businessId(deadTask.getBusinessId())
                .taskVersion(deadTask.getTaskVersion() + 1)
                .status(JudgeTaskStatus.PENDING)
                .maxAttempts(deadTask.getMaxAttempts())
                .build();
        judgeTaskMapper.insert(newTask);
        return newTask.getId();
    }

    /**
     * 获取任务详情
     */
    @Override
    public JudgeTaskDetailVo getJudgeTaskDetailById(Long taskId) {
        JudgeTask task = judgeTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BaseException("任务不存在");
        }

        JudgeTaskDetailVo vo = BeanUtil.copyProperties(task, JudgeTaskDetailVo.class);

        Submission submission = submissionMapper.selectById(task.getBusinessId());
        if (submission == null) {
            throw new BaseException("提交不存在");
        }
        Long userId = submission.getUserId();
        Long problemId = submission.getProblemId();
        Long contestId = submission.getContestId();

        User user = userMapper.selectById(userId);
        Problem problem = problemMapper.selectById(problemId);

        vo.setUserId(userId);
        vo.setUserName(user.getUsername());

        vo.setProblemId(problemId);
        vo.setProblemName(problem.getProblemName());

        if(contestId != null){
            Contest contest = contestMapper.selectById(contestId);
            vo.setContestId(contestId);
            vo.setContestTitle(contest.getTitle());
        }

        return vo;
    }

    /**
     * 恢复提交为待测评状态。
     */
    private void resetSubmissionForRejudge(Long submissionId, Integer version) {
        Submission submission = submissionMapper.selectOne(
                Wrappers.<Submission>lambdaQuery()
                        .eq(Submission::getId, submissionId)
                        .last("FOR UPDATE")
        );

        if (submission == null) {
            throw new BaseException("提交不存在：" + submissionId);
        }

        if (JudgingConstant.TERMINAL_STATUSES.contains(submission.getStatus())
                && !SystemConstant.SYSTEM_ERROR.equals(submission.getStatus())) {
            throw new BaseException("该提交已经存在有效测评结果，不能按死亡任务重测：" + submissionId);
        }

        int affectedRows = submissionMapper.update(
                Wrappers.<Submission>lambdaUpdate()
                        .set(Submission::getStatus, JudgingConstant.IN_QUEUE)
                        .set(Submission::getScore, 0)
                        .set(Submission::getVersion, version)
                        .set(Submission::getTimeMs, null)
                        .set(Submission::getMemoryKb, null)
                        .set(Submission::getCompilerMsg, null)
                        .set(Submission::getJudgeMsg, null)
                        .set(Submission::getJudgeStartTime, null)
                        .set(Submission::getJudgeEndTime, null)
                        .eq(Submission::getId, submissionId)
        );

        if (affectedRows != 1) {
            throw new BaseException("恢复提交状态失败：" + submissionId);
        }
    }

}
