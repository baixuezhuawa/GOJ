package com.gusl.gojserver.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.common.constant.JudgeQueueConstant;
import com.gusl.common.constant.JudgingConstant;
import com.gusl.gojserver.mapper.SubmissionMapper;
import com.gusl.gojserver.pojo.dto.SubmissionDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojserver.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl extends ServiceImpl<SubmissionMapper, Submission> implements SubmissionService {

    private final SubmissionMapper submissionMapper;

    private final StringRedisTemplate redisTemplate;

    /**
     * 将用户的提交, 提交到测评机
     * @param submissionDto 提交信息
     */
    @Override
    public void submitProblemToJudge(SubmissionDto submissionDto, LoginUser loginUser) {
        // 将submission写入数据库中
        // 设置测评状态 在队列
        Submission submission = BeanUtil.copyProperties(submissionDto, Submission.class);
        submission.setStatus(JudgingConstant.IN_QUEUE);
        submission.setUserId(loginUser.getUserId());
        submissionMapper.insert(submission);
        log.info("提交用户id:{} 测评问题id:{} 测评id:{} ----- 开始测评",
                submission.getUserId(),
                submission.getProblemId(),
                submission.getId()
        );
        // 构造测评请求... v1 版本暂时跳过
        // 通过redis队列充当消息队列发送请求.
        redisTemplate.opsForList().leftPush(JudgeQueueConstant.READY_QUEUE, submission.getId().toString());
        log.info("submissionId:{} in queue", submission.getId());
    }
}
