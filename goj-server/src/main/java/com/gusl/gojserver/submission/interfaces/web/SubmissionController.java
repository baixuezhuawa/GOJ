package com.gusl.gojserver.submission.interfaces.web;

import com.gusl.common.common.BaseController;
import com.gusl.common.common.Result;
import com.gusl.gojserver.submission.application.dto.Submission2JudgeDto;
import com.gusl.gojserver.account.infrastructure.security.LoginUser;
import com.gusl.gojserver.submission.application.vo.SubmissionDetailVo;
import com.gusl.gojserver.submission.application.vo.SubmissionListVo;
import com.gusl.gojserver.submission.application.vo.SubmissionVo;
import com.gusl.gojserver.submission.application.SubmissionService;
import com.gusl.gojserver.submission.application.SubmissionIdempotencyService;
import com.gusl.gojserver.submission.application.model.SubmissionRequestIdentity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "提交管理")
@RestController
@RequestMapping("/submission")
@RequiredArgsConstructor
public class SubmissionController extends BaseController {

    private final SubmissionService submissionService;
    private final SubmissionIdempotencyService submissionIdempotencyService;


    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "万恶源头")
    @PostMapping("/submit")
    public Result submission(@RequestBody Submission2JudgeDto submission2JudgeDto, @AuthenticationPrincipal LoginUser loginUser) {
        SubmissionRequestIdentity identity = SubmissionRequestIdentity.regular(submission2JudgeDto);
        SubmissionVo submissionVo = submissionIdempotencyService.execute(loginUser.getUserId(), identity,
                () -> submissionService.submitProblemToJudge(submission2JudgeDto, loginUser, identity));
        return success("你肯定会AC的, 直接看下一题吧! 哈哈哈", submissionVo);
    }

    /**
     * 开放接口, 获取提交详情
     */
    @Operation(summary = "根据提交id查看提交信息")
    @GetMapping("/{submissionId}")
    public Result submitStatus(@PathVariable Long submissionId) {
        SubmissionDetailVo vo = submissionService.getSubmissionById(submissionId);
        return success("操作成功", vo);
    }

}
