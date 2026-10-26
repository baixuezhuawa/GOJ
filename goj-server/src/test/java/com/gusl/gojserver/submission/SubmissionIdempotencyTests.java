package com.gusl.gojserver.submission;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.gusl.common.common.BaseException;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojserver.contest.application.dto.ContestSubmission2JudgeDto;
import com.gusl.gojserver.submission.application.SubmissionIdempotencyService;
import com.gusl.gojserver.submission.application.dto.Submission2JudgeDto;
import com.gusl.gojserver.submission.application.model.SubmissionRequestIdentity;
import com.gusl.gojserver.submission.application.model.SubmissionRequestException;
import com.gusl.gojserver.submission.application.vo.SubmissionVo;
import com.gusl.gojserver.submission.infrastructure.persistence.SubmissionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 提交幂等测试，验证请求身份、结果回放和异常分类。 */
class SubmissionIdempotencyTests {
    private final SubmissionMapper mapper = mock(SubmissionMapper.class);
    private final SubmissionIdempotencyService service = new SubmissionIdempotencyService(mapper);

    /** 初始化 MyBatis-Plus 的实体字段缓存。 */
    @BeforeEach
    void prepare() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Submission.class);
    }

    /** 缺失、缩写和非法 UUID 均拒绝，大小写统一为同一身份。 */
    @Test
    void validatesUuidAndPreservesExactContent() {
        Submission2JudgeDto dto = dto();
        for (String invalid : new String[]{null, "", "1-1-1-1-1", "not-a-uuid"}) {
            dto.setRequestId(invalid);
            assertThrows(BaseException.class, () -> SubmissionRequestIdentity.regular(dto));
        }
        dto.setRequestId(UUID.randomUUID().toString().toUpperCase());
        SubmissionRequestIdentity identity = SubmissionRequestIdentity.regular(dto);
        assertEquals(dto.getRequestId().toLowerCase(), identity.getRequestId());
        dto.setSourceCode(dto.getSourceCode() + " ");
        assertNotEquals(identity.getRequestSha256(), SubmissionRequestIdentity.regular(dto).getRequestSha256());
    }

    /** 指纹区分入口、题目、语言及比赛题号。 */
    @Test
    void fingerprintBindsAllRequestFields() {
        Submission2JudgeDto dto = dto();
        String original = SubmissionRequestIdentity.regular(dto).getRequestSha256();
        dto.setProblemId(2L);
        assertNotEquals(original, SubmissionRequestIdentity.regular(dto).getRequestSha256());
        dto.setProblemId(1L);
        dto.setLanguage("java11");
        assertNotEquals(original, SubmissionRequestIdentity.regular(dto).getRequestSha256());
        ContestSubmission2JudgeDto contest = new ContestSubmission2JudgeDto();
        contest.setRequestId(dto.getRequestId());
        contest.setLanguage("py3");
        contest.setSourceCode(dto.getSourceCode());
        String first = SubmissionRequestIdentity.contest(1L, "A", contest).getRequestSha256();
        assertNotEquals(original, first);
        assertNotEquals(first, SubmissionRequestIdentity.contest(1L, "B", contest).getRequestSha256());
        assertNotEquals(first, SubmissionRequestIdentity.contest(2L, "A", contest).getRequestSha256());
    }

    /** 已受理的请求无需再调用业务校验或创建任务。 */
    @Test
    void replaysWithoutCallingCreation() {
        SubmissionRequestIdentity identity = SubmissionRequestIdentity.regular(dto());
        when(mapper.findAcceptedRequest(any(), any())).thenReturn(saved(identity));
        Supplier<SubmissionVo> create = mock(Supplier.class);
        assertEquals(42L, service.execute(1L, identity, create).getSubmissionId());
        verifyNoInteractions(create);
    }

    /** 竞争者先完成时，唯一键冲突或频率拒绝均回放获胜记录。 */
    @Test
    void rechecksAfterDatabaseOrBusinessFailure() {
        SubmissionRequestIdentity identity = SubmissionRequestIdentity.regular(dto());
        for (RuntimeException failure : new RuntimeException[]{
                new DuplicateKeyException("唯一键冲突"), new BaseException("频率过高")}) {
            when(mapper.findAcceptedRequest(any(), any())).thenReturn(null, saved(identity));
            assertEquals(42L, service.execute(1L, identity, () -> { throw failure; }).getSubmissionId());
        }
    }

    /** 同 UUID 的内容冲突与明确业务拒绝使用不同状态。 */
    @Test
    void distinguishesConflictAndRejection() {
        SubmissionRequestIdentity identity = SubmissionRequestIdentity.regular(dto());
        Submission existing = saved(identity);
        existing.setRequestSha256("different");
        when(mapper.findAcceptedRequest(any(), any())).thenReturn(existing);
        assertEquals(409, assertThrows(SubmissionRequestException.class,
                () -> service.execute(1L, identity, () -> null)).getStatus());
        when(mapper.findAcceptedRequest(any(), any())).thenReturn(null);
        assertEquals(422, assertThrows(SubmissionRequestException.class,
                () -> service.execute(1L, identity, () -> { throw new BaseException("题目未发布"); })).getStatus());
    }

    /** 无受理记录的数据库错误仍然抛出，不能伪造成成功或明确业务拒绝。 */
    @Test
    void preservesUnknownDatabaseFailure() {
        SubmissionRequestIdentity identity = SubmissionRequestIdentity.regular(dto());
        DuplicateKeyException failure = new DuplicateKeyException("其他唯一键冲突");
        assertSame(failure, assertThrows(DuplicateKeyException.class,
                () -> service.execute(1L, identity, () -> { throw failure; })));
    }

    /** 构造普通提交请求。 */
    private Submission2JudgeDto dto() {
        return new Submission2JudgeDto(UUID.randomUUID().toString(), 1L, "py3", "print(1)", null);
    }

    /** 构造已受理记录。 */
    private Submission saved(SubmissionRequestIdentity identity) {
        Submission submission = new Submission();
        submission.setId(42L);
        submission.setSubmissionType("REGULAR");
        submission.setRequestSha256(identity.getRequestSha256());
        return submission;
    }
}
