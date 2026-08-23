package com.gusl.gojserver.controller;

import com.gusl.common.common.BaseController;
import com.gusl.common.common.PageQuery;
import com.gusl.common.common.PageResult;
import com.gusl.common.common.Result;
import com.gusl.gojserver.pojo.dto.ContestSubmission2JudgeDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.pojo.vo.*;
import com.gusl.gojserver.service.ContestParticipateService;
import com.gusl.gojserver.service.ContestService;
import com.gusl.gojserver.service.ContestSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@Validated
@Tag(name = "比赛接口")
@RestController
@RequestMapping("/contest")
@RequiredArgsConstructor
public class ContestController extends BaseController {

    private final ContestService contestService;

    private final ContestParticipateService contestParticipateService;

    private final ContestSubmissionService contestSubmissionService;



    @Operation(summary = "获取未结束比赛")
    @GetMapping("/unfinish-list")
    public Result unfinishContestList(
            @ModelAttribute PageQuery pageQuery,
            @AuthenticationPrincipal LoginUser loginUser
    ){
        PageResult<ContestListVo> list = contestService.getUnfinishContest(pageQuery, loginUser);
        return success("操作成功", list);
    }


    @Operation(summary = "获取结束比赛列表")
    @GetMapping("/finish-list")
    public Result finishContestList(
            @ModelAttribute PageQuery pageQuery,
            @AuthenticationPrincipal LoginUser loginUser
    ){
        PageResult<ContestListVo> list = contestService.getFinishContest(pageQuery, loginUser);
        return success("操作成功", list);
    }



    @Operation(summary = "报名比赛")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/register-contest/{contestId}")
    public Result registerContest(
            @PathVariable @Positive(message = "比赛ID必须大于0") Long contestId,
            @AuthenticationPrincipal LoginUser loginUser
    ){
        contestParticipateService.registerContest(contestId, loginUser);
        return success("操作成功");
    }



    @Operation(summary = "取消报名比赛")
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/logout-contest/{contestId}")
    public Result logoutContest(
            @PathVariable Long contestId,
            @AuthenticationPrincipal LoginUser loginUser
    ){
        contestParticipateService.logoutContest(contestId, loginUser);
        return success();
    }



    @Operation(summary = "比赛详情")
    @GetMapping("/{contestId}")
    public Result contestInfo(@PathVariable Long contestId){
        ContestDetailVo vo = contestService.getContestDetail(contestId);
        return success("操作成功", vo);
    }


    @Operation(summary = "获取比赛题目详细信息")
    @GetMapping("/{contestId}/problem/{problemCode}")
    public Result getContestProblem(
            @PathVariable Long contestId,
            @PathVariable String problemCode
    ){
        ContestProblemDetailVo vo = contestService.getContestProblemDetail(contestId, problemCode);
        return success("操作成功", vo);
    }



    @Operation(summary = "提交")
    @PostMapping("/{contestId}/problem/{problemCode}")
    public Result submitContestProblem(
            @PathVariable Long contestId,
            @PathVariable String problemCode,
            @Valid  @RequestBody ContestSubmission2JudgeDto submission,
            @AuthenticationPrincipal LoginUser loginUser
    ){
        SubmissionVo vo = contestSubmissionService.submitContestProblemToJudge(
                contestId,
                problemCode,
                submission,
                loginUser
        );
        return success("提交成功", vo);
    }



    @Operation(summary = "获取比赛提交列表")
    @GetMapping("/{contestId}/submission/list")
    public Result getContestSubmissionList(
            @PathVariable Long contestId,
            @ModelAttribute PageQuery pageQuery,
            @AuthenticationPrincipal LoginUser loginUser
    ){
        PageResult<ContestSubmissionListVo> result =
                contestSubmissionService.getContestSubmissionList(contestId, pageQuery,  loginUser);
        return success("操作成功", result);
    }


    @Operation(summary = "获取赛时提交详情")
    @GetMapping("/{contestId}/submission/{submissionId}")
    public Result getContestSubmissionDetail(
            @PathVariable Long contestId,
            @PathVariable Long submissionId,
            @AuthenticationPrincipal LoginUser loginUser
    ){
        ContestSubmissionDetailVo vo =
                contestSubmissionService.getContestSubmissionDetail(contestId, submissionId, loginUser);
        return success("操作成功", vo);
    }

}
