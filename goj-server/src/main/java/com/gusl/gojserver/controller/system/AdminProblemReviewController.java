package com.gusl.gojserver.controller.system;

import com.gusl.common.common.BaseController;
import com.gusl.common.common.Result;
import com.gusl.gojserver.pojo.dto.ProblemReviewJudgeDto;
import com.gusl.gojserver.pojo.dto.ProblemReviewRejectDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.service.ProblemReviewJudgeService;
import com.gusl.gojserver.service.ProblemReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员题目审核接口。
 */
@Tag(name = "管理员-题目审核")
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/problem-draft-review")
@PreAuthorize("hasAuthority('problem:manage')")
public class AdminProblemReviewController extends BaseController {

    private final ProblemReviewService problemReviewService;
    private final ProblemReviewJudgeService problemReviewJudgeService;

    /**
     * 分页获取待审核题目列表。
     */
    @Operation(summary = "待审核题目列表")
    @GetMapping
    public Result getPendingReviews(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return success("操作成功", problemReviewService.getPendingReviews(page, size));
    }

    /**
     * 获取待审核题目详情。
     */
    @Operation(summary = "待审核题目详情")
    @GetMapping("/{problemId}")
    public Result getPendingReviewDetail(@PathVariable Long problemId) {
        return success("操作成功", problemReviewService.getPendingReviewDetail(problemId));
    }

    /**
     * 提交管理员验题代码，返回进入独立验题队列的 reviewSubmissionId。
     */
    @Operation(summary = "提交验题代码")
    @PostMapping("/{problemId}/judge-submissions")
    public Result judgePendingProblem(
            @PathVariable Long problemId,
            @RequestBody ProblemReviewJudgeDto dto,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        Long reviewSubmissionId = problemReviewJudgeService.submit(problemId, dto, loginUser);
        return success("验题任务已进入队列", reviewSubmissionId);
    }

    /**
     * 获取管理员验题任务状态。
     */
    @Operation(summary = "获取验题状态")
    @GetMapping("/judge-submissions/{reviewSubmissionId}")
    public Result getReviewSubmission(@PathVariable Long reviewSubmissionId) {
        return success("操作成功", problemReviewJudgeService.getReviewSubmission(reviewSubmissionId));
    }

    /**
     * 审核通过并发布题目。
     */
    @Operation(summary = "审核通过")
    @PostMapping("/{problemId}/approve")
    public Result approve(@PathVariable Long problemId) {
        problemReviewService.approve(problemId);
        return success("题目审核通过");
    }

    /**
     * 驳回题目并记录原因。
     */
    @Operation(summary = "审核驳回")
    @PostMapping("/{problemId}/reject")
    public Result reject(
            @PathVariable Long problemId,
            @RequestBody ProblemReviewRejectDto dto
    ) {
        problemReviewService.reject(problemId, dto.getRemark());
        return success("题目已驳回");
    }
}
