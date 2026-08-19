package com.gusl.gojserver.controller;

import com.gusl.common.common.BaseController;
import com.gusl.common.common.PageQuery;
import com.gusl.common.common.PageResult;
import com.gusl.common.common.Result;
import com.gusl.gojserver.pojo.dto.SubmissionSearchDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.pojo.vo.*;
import com.gusl.gojserver.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "用户做题中心")
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class UserProfileController extends BaseController {

    private final SubmissionService submissionService;

    private final UserProblemProgressService problemProgressService;

    private final UserSubmissionStatusStatService submissionStatusStatService;

    private final UserLanguageStatService userLanguageStatService;

    private final ProfileStatisticsService profileStatisticsService;

    @Operation(summary = "我的尝试过的题目")
    @GetMapping("/attempted-problem")
    public Result getAttemptedProblem(
            @ModelAttribute PageQuery pageQuery,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        PageResult<ProblemPageListVo> problemVos = problemProgressService.getAttemptedProblem(pageQuery, loginUser);
        return success("操作成功", problemVos);
    }


    @Operation(summary = "我通过的题目")
    @GetMapping("/solve-problem")
    public Result getSolveByMe(
            @ModelAttribute PageQuery pageQuery,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        PageResult<ProblemPageListVo> problemVos = problemProgressService.getSolveByMe(pageQuery, loginUser);
        return success("操作成功", problemVos);
    }


    @Operation(summary = "我的提交历史")
    @GetMapping("/submission-history")
    public Result getMySubmission(
            @AuthenticationPrincipal LoginUser loginUser,
            @ModelAttribute SubmissionSearchDto condition
    ) {
        PageResult<SubmissionListVo> list = submissionService.getMySubmissionList(loginUser, condition);
        return success("操作成功", list);
    }


    @Operation(summary = "我的提交状态统计")
    @GetMapping("/submission-status-stat")
    public Result getMySubmissionStatusStat(@AuthenticationPrincipal LoginUser loginUser) {
        List<SubmissionStatusStatVo> res = submissionStatusStatService.getMySubmissionStatusStat(loginUser);
        return success("操作成功", res);
    }

    /**
     * 获取当前用户的语言使用统计。
     */
    @Operation(summary = "我的语言使用统计")
    @GetMapping("/language-stat")
    public Result getMyLanguageStat(@AuthenticationPrincipal LoginUser loginUser) {
        List<LanguageStatVo> result = userLanguageStatService.getMyLanguageStat(loginUser);
        return success("操作成功", result);
    }


    /**
     * 获取当前用户的最近几次提交。
     */
    @Operation(summary = "我的最近提交")
    @GetMapping("/submission-recent")
    public Result getMyRecentSubmission(@AuthenticationPrincipal LoginUser loginUser) {
        PageResult<SubmissionListVo> list = submissionService.getMyRecentSubmission(loginUser);
        return success("操作成功", list);
    }

    /**
     * 个人主页做题信息概览
     */
    @Operation(summary = "个人主页统计")
    @GetMapping("/profile-statistics")
    public Result getProfileStatistics(@AuthenticationPrincipal LoginUser loginUser) {
        ProfileStatisticsVo vo = profileStatisticsService.getProfileStatistics(loginUser);
        return success("操作成功", vo);
    }


}
