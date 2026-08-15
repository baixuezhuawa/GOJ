package com.gusl.gojserver.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.common.common.BaseException;
import com.gusl.common.constant.JudgeQueueConstant;
import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.utils.StringUtils;
import com.gusl.gojserver.config.properties.SubmissionProperties;
import com.gusl.gojserver.mapper.SubmissionMapper;
import com.gusl.gojserver.mapper.UserMapper;
import com.gusl.gojserver.pojo.dto.SubmissionDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojserver.pojo.vo.SubmissionVo;
import com.gusl.gojserver.service.SubmissionService;
import com.gusl.gojserver.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl extends ServiceImpl<SubmissionMapper, Submission> implements SubmissionService {

    private final SubmissionMapper submissionMapper;

    private final StringRedisTemplate redisTemplate;

    private final SubmissionProperties submissionProperties;

    private final UserMapper userMapper;

    /**
     * 将用户的提交, 提交到测评机
     * @param submissionDto 提交信息
     */
    @Override
    public void submitProblemToJudge(SubmissionDto submissionDto, LoginUser loginUser) {
        // 检测提交的合法性
        illicitDetection(submissionDto);

        // 将submission写入数据库中
        // 设置测评状态 在队列
        Submission submission = BeanUtil.copyProperties(submissionDto, Submission.class);
        submission.setStatus(JudgingConstant.IN_QUEUE);
        submission.setUserId(loginUser.getUserId());
        submission.setSubmissionTime(LocalDateTime.now()); // 设置提交时间
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

    @Override
    public SubmissionVo getSubmissionById(Long submissionId) {
        SubmissionVo vo = new SubmissionVo();
        Submission submission = getById(submissionId);
        BeanUtil.copyProperties(submission, vo);
        vo.setUsername(userMapper.selectById(submission.getUserId()).getUsername());
        return vo;
    }

    private void illicitDetection(SubmissionDto dto) {
        if(StringUtils.isEmpty(dto.getSourceCode())){
            throw new BaseException("代码不能为空");
        }
        if(dto.getSourceCode().length() > submissionProperties.getMaxCodeLength()){
            throw new BaseException("代码长度超出最大限制");
        }
        AtomicBoolean flag = new AtomicBoolean(false);
        submissionProperties.getLanguages().forEach(supportedLanguage -> {
            if(supportedLanguage.isEnabled() && supportedLanguage.getCode().equals(dto.getLanguage())){
                flag.set(true);
            }
        });
        if(!flag.get()){
            throw new BaseException("不是可支持的语言类型");
        }
    }
}
