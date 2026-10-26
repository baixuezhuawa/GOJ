package com.gusl.gojserver.submission.application.model;

import com.gusl.common.common.BaseException;
import lombok.Getter;

/** 提交请求异常，区分明确未受理与请求身份冲突，避免前端把网络错误当成拒绝。 */
@Getter
public class SubmissionRequestException extends BaseException {

    private final int status;

    /** 保存提交入口专用的 HTTP 状态和中文提示。 */
    public SubmissionRequestException(int status, String message) {
        super(message);
        this.status = status;
    }
}
