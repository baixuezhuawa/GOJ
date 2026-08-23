package com.gusl.gojserver.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.constant.JudgeTaskStatus;
import com.gusl.common.constant.JudgeTaskType;
import com.gusl.common.pojo.entity.JudgeTask;
import com.gusl.common.pojo.entity.JudgeTaskMessage;
import com.gusl.gojserver.mapper.JudgeTaskMapper;
import com.gusl.gojserver.service.JudgeTaskPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务调度器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeTaskDispatcher {

    private final JudgeTaskMapper judgeTaskMapper;

    private final JudgeTaskPublisher judgeTaskPublisher;




    /**
     * 定时发送待调度的测评任务。
     */
    @Scheduled(fixedDelayString = "${goj.judge.task.dispatch-interval-ms:3000}")
    public void dispatchPendingTasks(){

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime redispatchBefore = now.minusSeconds(30);

        // 查询第一次新任务
        List<JudgeTask> contest = searchTask(JudgeTaskType.CONTEST_SUBMISSION, redispatchBefore);
        List<JudgeTask> adminTask = searchTask(JudgeTaskType.PROBLEM_REVIEW, redispatchBefore);
        List<JudgeTask> regular = searchTask(JudgeTaskType.SUBMISSION, redispatchBefore);

        // 查询已经到达重试时间的任务。
        List<JudgeTask> retryTasks = judgeTaskMapper.selectList(
                Wrappers.<JudgeTask>lambdaQuery()
                        .eq(JudgeTask::getStatus, JudgeTaskStatus.RETRY_WAIT)
                        .le(JudgeTask::getNextRetryTime, now)
                        .and(wrapper -> wrapper
                                .isNull(JudgeTask::getLastDispatchTime)
                                .or()
                                .lt(JudgeTask::getLastDispatchTime, redispatchBefore)
                        )
                        .orderByAsc(JudgeTask::getId)
                        .last("LIMIT 100")
        );


        // 优先发送比赛任务
        for (JudgeTask task : contest) dispatchOne(task);

        // 验题任务
        for(JudgeTask task : adminTask) dispatchOne(task);

        // 普通任务
        for(JudgeTask task : regular) dispatchOne(task);

        // 重试任务
        for (JudgeTask task : retryTasks) dispatchOne(task);

    }



    /**
     * 搜索任务
     * @param taskType 任务类型
     * @param redispatchBefore 重新派遣时间
     * @return 任务列表
     */
    private List<JudgeTask> searchTask(String taskType, LocalDateTime redispatchBefore){
        return judgeTaskMapper.selectList(
                Wrappers.<JudgeTask>lambdaQuery()
                        .eq(JudgeTask::getTaskType, taskType)
                        .eq(JudgeTask::getStatus, JudgeTaskStatus.PENDING)
                        .and(wrapper -> wrapper
                                .isNull(JudgeTask::getLastDispatchTime)
                                .or()
                                .lt(JudgeTask::getLastDispatchTime, redispatchBefore)
                        )
                        .orderByAsc(JudgeTask::getId)
                        .last("LIMIT 100")
        );
    }



    /**
     * 发布测评任务
     * @param task 测评任务
     */
    public void dispatchOne(JudgeTask task){
        try{
            JudgeTaskMessage message = new JudgeTaskMessage(
                    1,
                    task.getId(),
                    task.getTaskType(),
                    task.getBusinessId(),
                    task.getTaskVersion(),
                    LocalDateTime.now()
            );

            // 发布消息
            judgeTaskPublisher.publish(message);

            judgeTaskMapper.update(
                    Wrappers.<JudgeTask> lambdaUpdate()
                            .set(JudgeTask::getLastDispatchTime, LocalDateTime.now())
                            .eq(JudgeTask::getId, task.getId())
                            // 只有状态仍处于扫描得的任务列表是才更新
                            .eq(JudgeTask::getStatus, task.getStatus())
            );

        }catch (Exception e){
            log.error("测评任务发布失败, taskId:{}", task.getId(), e);
        }
    }


}
