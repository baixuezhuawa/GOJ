package com.gusl.gojserver.submission.application;

import com.gusl.common.common.BaseException;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojserver.submission.application.model.SubmissionRequestIdentity;
import com.gusl.gojserver.submission.application.model.SubmissionRequestException;
import com.gusl.gojserver.submission.application.vo.SubmissionVo;
import com.gusl.gojserver.submission.infrastructure.persistence.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 提交幂等协调服务，在创建事务外查询并回放已经受理的提交。
 */
@Service
@RequiredArgsConstructor
public class SubmissionIdempotencyService {

    private final SubmissionMapper submissionMapper;

    /** 回放已有提交，或调用另一个 Spring Bean 的事务方法创建提交。 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SubmissionVo execute(Long userId, SubmissionRequestIdentity identity, Supplier<SubmissionVo> create) {
        // 先查询已受理记录，避免当前比赛阶段或频率限制阻断回放。
        Submission existing = find(userId, identity.getRequestId());
        if (existing != null) {
            return replay(existing, identity);
        }

        try {
            // Supplier 调用业务 Service 代理，返回时创建事务已经提交。
            return create.get();
        } catch (BaseException | DataAccessException | TransactionException exception) {
            // 读取创建事务结束后重新；当前项目使用单一数据源，无从库路由。
            // 除唯一键竞争外，也处理另一请求先提交导致本请求被频率限制拒绝的情况。
            try {
                existing = find(userId, identity.getRequestId());
            } catch (DataAccessException readException) {
                exception.addSuppressed(readException);
                throw exception;
            }

            if (existing != null) {
                return replay(existing, identity);
            }

            // 只有业务拒绝且未找到受理记录，才允许前端结束本次尝试。
            if (exception instanceof BaseException) {
                throw new SubmissionRequestException(422, exception.getMessage());
            }

            throw exception;
        }
    }

    /** 根据当前登录用户和请求 UUID 查找提交。 */
    private Submission find(Long userId, String requestId) {
        return submissionMapper.findAcceptedRequest(userId, requestId);
    }

    /** 同一 UUID 仅允许回放相同内容，不返回不断变化的评测结果。 */
    private SubmissionVo replay(Submission submission, SubmissionRequestIdentity identity) {
        if (!Objects.equals(submission.getRequestSha256(), identity.getRequestSha256())) {
            throw new SubmissionRequestException(409, "同一 requestId 不能用于不同的提交内容");
        }
        return new SubmissionVo(submission.getSubmissionType(), submission.getId());
    }
}
