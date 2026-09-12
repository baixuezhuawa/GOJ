package com.gusl.gojserver.controller;

import com.gusl.common.common.BaseController;
import com.gusl.common.common.PageQuery;
import com.gusl.common.common.PageResult;
import com.gusl.common.common.Result;
import com.gusl.gojserver.pojo.dto.ProblemPageListDto;
import com.gusl.gojserver.pojo.dto.UpdateProblemDraftDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.pojo.vo.ProblemDraftListVo;
import com.gusl.gojserver.pojo.vo.ProblemPageListVo;
import com.gusl.gojserver.pojo.vo.UploadProblemDetailVo;
import com.gusl.gojserver.service.ProblemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@Tag(name = "问题管理", description = "问题相关接口")
@RestController
@RequestMapping("/problem")
@RequiredArgsConstructor
public class ProblemController extends BaseController {

    private final ProblemService problemService;


    @Operation(summary = "题目列表")
    @GetMapping("/list")
    public Result problemList(
            @ModelAttribute ProblemPageListDto dto,
            @AuthenticationPrincipal LoginUser loginUser
    ){
        PageResult<ProblemPageListVo> records = problemService.getProblemList(dto, loginUser);
        return success("操作成功", records);
    }

    @Operation(summary = "题目详情")
    @GetMapping("/{problemId}")
    public Result getProblemById(@PathVariable("problemId") Long id){
        return success("操作成功", problemService.getProblemInfoById(id));
    }

    @PreAuthorize("isAuthenticated()") // 必须登录
    @Operation(summary = "获取已上传题目列表")
    @GetMapping("/upload-problem-list")
    public Result getUploadProblemList(
            @ModelAttribute PageQuery pageQuery,
            @AuthenticationPrincipal LoginUser loginUser
    ){
        PageResult<ProblemDraftListVo> list = problemService.getUploadProblemList(pageQuery, loginUser);
        return success("操作成功", list);
    }

    @PreAuthorize("isAuthenticated()") // 必须登录
    @Operation(summary = "查看问题的问题详情")
    @GetMapping("/upload-problem-detail/{problemId}")
    public Result getUploadProblemDetail(
            @PathVariable Long problemId,
            @AuthenticationPrincipal LoginUser loginUser
    ){
        UploadProblemDetailVo vo = problemService.getUploadProblemDetail(problemId, loginUser);
        return success("操作成功", vo);
    }

    // 只能修改草稿阶段的问题
    @PreAuthorize("isAuthenticated()") // 必须登录
    @Operation(summary = "编辑草稿阶段的问题")
    @PostMapping("/update-myProblem-draft")
    public Result updateMyProblem(
            @RequestBody UpdateProblemDraftDto updateproblemDraftDto,
            @AuthenticationPrincipal LoginUser loginUser
    ){
        problemService.updateMyProblemDraft(updateproblemDraftDto, loginUser);
        return success();
    }

    @PreAuthorize("isAuthenticated()") // 必须登录
    @Operation(summary = "删除我的草稿问题")
    @DeleteMapping("/{problemId}")
    public Result deleteMyProblem(
            @PathVariable Long problemId,
            @AuthenticationPrincipal LoginUser loginUser
    ){
        problemService.deleteMyProblemDraft(problemId, loginUser);
        return success();
    }

    @Operation(summary = "修改我的题目状态：1 已发布，5 准备中")
    @PutMapping("/update-myProblem-status/{problemId}")
    public Result updateMyProblemStatus(
            @PathVariable Long problemId,
            @RequestParam Integer status,
            @AuthenticationPrincipal LoginUser loginUser
    ){
        problemService.updateMyProblemStatus(problemId, status, loginUser);
        return success();
    }
}
