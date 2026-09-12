package com.gusl.gojserver.controller.system;

import com.gusl.common.common.BaseController;
import com.gusl.common.common.PageResult;
import com.gusl.common.common.Result;
import com.gusl.common.constant.ProblemStatus;
import com.gusl.gojserver.pojo.dto.ProblemDraftDto;
import com.gusl.gojserver.pojo.dto.ProblemPageListDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.pojo.vo.AdminProblemPageListVo;
import com.gusl.gojserver.pojo.vo.TotalProblemInfoVo;
import com.gusl.gojserver.service.ProblemReviewService;
import com.gusl.gojserver.service.ProblemService;
import com.gusl.gojserver.service.ProblemTestDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Tag(name = "管理员-问题管理")
@RestController
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequestMapping("/admin/problem-manager")
@RequiredArgsConstructor
public class AdminProblemController extends BaseController {

    private final ProblemService problemService;

    private final ProblemReviewService problemReviewService;

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

    /**
     * 管理员直接上传题目，上传成功后发布测试数据并进入准备状态。
     */
    @Operation(summary = "管理员直接上传题目")
    @PostMapping(value = "/problems", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result uploadProblem(
            @RequestPart("draft") ProblemDraftDto draftDto,
            @RequestPart("file") MultipartFile data,
            @AuthenticationPrincipal LoginUser loginUser
    ) throws IOException {
        // 复用普通上传流程，完成题目创建、ZIP 校验并进入待审核状态。
        Long problemId = problemService.uploadProblemByUser(draftDto, data, loginUser);

        // 复用审核通过流程，正式发布测试数据并将题目修改为准备状态。
        problemReviewService.approve(problemId);

        return success("题目上传成功", Map.of(
                "problemId", problemId,
                "status", ProblemStatus.PREPARE
        ));
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


    @Operation(summary = "上传测试数据")
    @PostMapping(value = "/problems/{problemId}/test-data", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result uploadProblemTestData(
            @PathVariable Long problemId,
            @RequestPart("file") MultipartFile data,
            @RequestPart("remark") String remark
    ){
        problemTestDataService.uploadTestDataByAdmin(problemId, data, remark);
        return success("操作成功");
    }



    @Operation(summary = "启用测试数据")
    @PutMapping("/problems/{problemId}/test-data")
    public Result enableProblemTestData(
            @PathVariable Long problemId,
            @RequestParam Integer version
    ){
        problemTestDataService.enableProblemTestDataVersion(problemId, version);
        return success("操作成功");
    }


    @Operation(summary = "删除测试数据")
    @DeleteMapping("/problems/{problemId}/test-data")
    public Result deleteProblemTestData(
            @PathVariable Long problemId,
            @RequestParam Integer version
    ){
        problemTestDataService.deleteProblemTestDataVersion(problemId, version);
        return success("操作成功");
    }
}
