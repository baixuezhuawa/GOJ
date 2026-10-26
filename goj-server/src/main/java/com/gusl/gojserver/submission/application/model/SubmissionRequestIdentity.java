package com.gusl.gojserver.submission.application.model;

import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import com.gusl.common.common.BaseException;
import com.gusl.gojserver.contest.application.dto.ContestSubmission2JudgeDto;
import com.gusl.gojserver.submission.application.dto.Submission2JudgeDto;
import lombok.Getter;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 提交请求身份，绑定一次逻辑提交的 UUID 和原始请求内容。
 */
@Getter
public class SubmissionRequestIdentity {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );

    private final String requestId;
    private final String requestSha256;

    /** 校验 UUID，并对固定顺序的 JSON 数组计算请求指纹。 */
    private SubmissionRequestIdentity(String requestId, Object[] content) {
        // 如果为空, 或者规则不匹配则直接抛异常
        if (requestId == null || !UUID_PATTERN.matcher(requestId).matches()) {
            throw new BaseException("requestId 必须是完整的 UUID");
        }

        this.requestId = requestId.toLowerCase(Locale.ROOT);

        // Fastjson 转义字段边界，Hutool 按 UTF-8 计算 SHA-256；源代码不裁剪或改写。
        this.requestSha256 = DigestUtil.sha256Hex(JSON.toJSONString(content));
    }

    /** 构建普通入口的请求身份，不依赖题目当前状态。 */
    public static SubmissionRequestIdentity regular(Submission2JudgeDto dto) {
        return new SubmissionRequestIdentity(
                dto.getRequestId(),
                new Object[]{"REGULAR", dto.getProblemId(), dto.getContestId(), dto.getLanguage(), dto.getSourceCode()}
        );
    }

    /** 构建比赛入口的请求身份，转普通提交时仍保留此身份。 */
    public static SubmissionRequestIdentity contest(Long contestId, String problemCode, ContestSubmission2JudgeDto dto) {
        return new SubmissionRequestIdentity(dto.getRequestId(), new Object[]{
                "CONTEST", contestId, problemCode, dto.getLanguage(), dto.getSourceCode()
        });
    }
}
