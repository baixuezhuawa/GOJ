package com.gusl.gojserver.problem.interfaces.web;

import com.gusl.common.common.BaseController;
import com.gusl.common.common.PageResult;
import com.gusl.common.common.Result;
import com.gusl.common.constant.ProblemStatus;
import com.gusl.gojserver.problem.application.dto.ProblemSaveDto;
import com.gusl.gojserver.problem.application.dto.ProblemPageListDto;
import com.gusl.gojserver.account.infrastructure.security.LoginUser;
import com.gusl.gojserver.problem.application.vo.AdminProblemPageListVo;
import com.gusl.gojserver.problem.application.vo.TotalProblemInfoVo;
import com.gusl.gojserver.problem.application.ProblemReviewJudgeService;
import com.gusl.gojserver.problem.application.dto.ProblemReviewJudgeDto;
import com.gusl.gojserver.problem.application.ProblemService;
import com.gusl.gojserver.problem.application.ProblemTestDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** 管理员题目管理接口，统一管理题面、数据版本和验题。 */
@Tag(name = "管理员-问题管理")
@RestController
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequestMapping("/admin/problem-manager")
@RequiredArgsConstructor
public class AdminProblemController extends BaseController {

    private final ProblemService problemService;

    private final ProblemReviewJudgeService problemReviewJudgeService;

    private final ProblemTestDataService problemTestDataService;


    @Operation(summary = "设置问题状态")
    @PutMapping("/{problemId}/set-status/{status}")
    public Result setProblemStatus(
            @PathVariable Long problemId,
            @PathVariable Integer status
    ){
        problemService.setProblemStatus(problemId, status);
        return success();
    }

    @Operation(summary = "设置问题难度")
    @PutMapping("/{problemId}/set-difficult")
    public Result setProblemDifficult(
            @PathVariable Long problemId,
            @RequestBody Integer difficult
    ){
        problemService.setProblemDifficultByAdmin(problemId, difficult);
        return success("操作成功");
    }


    @Operation(summary = "添加题目标签")
    @PutMapping("/{problemId}/add-tag/{tagId}")
    public Result addProblemTags(
            @PathVariable Long problemId,
            @PathVariable Long tagId
    ){
        problemService.addProblemTag(problemId, tagId);
        return success("操作成功");
    }


    @Operation(summary = "删除题目标签")
    @DeleteMapping("/{problemId}/add-tag/{tagId}")
    public Result deleteProblemTags(
            @PathVariable Long problemId,
            @PathVariable Long tagId
    ){
        problemService.deleteProblemTag(problemId, tagId);
        return success("操作成功");
    }

    /** 创建题目，测试数据通过独立接口上传。 */
    @Operation(summary = "管理员创建题目")
    @PostMapping("/problems")
    public Result createProblem(
            @RequestBody ProblemSaveDto dto,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        Long problemId = problemService.createProblem(dto, loginUser);
        return success("题目创建成功", Map.of("problemId", problemId, "status", ProblemStatus.DRAFT));
    }

    /** 编辑任意状态的题目信息。 */
    @Operation(summary = "管理员编辑题目")
    @PutMapping("/problems/{problemId}")
    public Result updateProblem(@PathVariable Long problemId, @RequestBody ProblemSaveDto dto) {
        problemService.updateProblem(problemId, dto);
        return success("题目保存成功");
    }

    @Operation(summary = "获取全部问题列表")
    @GetMapping("/problems")
    public Result getProblemSet(
            @ModelAttribute ProblemPageListDto dto,
            @AuthenticationPrincipal LoginUser loginUser
    ){
        PageResult<AdminProblemPageListVo> records = problemService.getProblemListByAdmin(dto, loginUser);
        return success("操作成功", records);
    }


    @Operation(summary = "获取题目完整信息")
    @GetMapping("/problems/{problemId}")
    public Result getProblemInfo(
            @PathVariable Long problemId
    ){
        TotalProblemInfoVo vo = problemService.getTotalProblemInfo(problemId);
        return success("操作成功", vo);
    }


    /** 新增或覆盖管理员指定版本的测试数据。 */
    @Operation(summary = "保存测试数据版本")
    @PutMapping(value = "/problems/{problemId}/test-data/{version}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result saveTestData(
            @PathVariable Long problemId,
            @PathVariable Integer version,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String remark
    ) {
        Long id = problemTestDataService.saveTestData(problemId, version, file, remark);
        return success("测试数据保存成功", id);
    }

    /** 启用或停用指定版本。 */
    @Operation(summary = "设置测试数据启用状态")
    @PutMapping("/problems/{problemId}/test-data/{version}/active")
    public Result setTestDataActive(
            @PathVariable Long problemId,
            @PathVariable Integer version,
            @RequestParam boolean active
    ) {
        problemTestDataService.setTestDataActive(problemId, version, active);
        return success("操作成功");
    }

    /** 管理员可删除任意版本。 */
    @Operation(summary = "删除测试数据版本")
    @DeleteMapping("/problems/{problemId}/test-data/{version}")
    public Result deleteTestData(@PathVariable Long problemId, @PathVariable Integer version) {
        problemTestDataService.deleteTestData(problemId, version);
        return success("操作成功");
    }

    /** 下载保存的原始 ZIP，固定响应文件名避免原文件名影响响应头。 */
    @Operation(summary = "下载测试数据压缩包")
    @GetMapping("/problems/{problemId}/test-data/{version}/archive")
    public ResponseEntity<Resource> downloadTestData(@PathVariable Long problemId, @PathVariable Integer version) {
        Resource archive = problemTestDataService.getArchive(problemId, version);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=problem-" + problemId + "-v" + version + ".zip")
                .body(archive);
    }

    /** 提交针对指定数据版本的管理员验题代码。 */
    @Operation(summary = "提交验题代码")
    @PostMapping("/problems/{problemId}/judge-submissions")
    public Result submitJudge(
            @PathVariable Long problemId,
            @RequestBody ProblemReviewJudgeDto dto,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return success("验题任务已进入队列", problemReviewJudgeService.submit(problemId, dto, loginUser));
    }

    /** 查询管理员验题结果。 */
    @Operation(summary = "获取验题结果")
    @GetMapping("/judge-submissions/{reviewSubmissionId}")
    public Result getReviewSubmission(@PathVariable Long reviewSubmissionId) {
        return success("操作成功", problemReviewJudgeService.getReviewSubmission(reviewSubmissionId));
    }
}
