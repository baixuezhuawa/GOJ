package com.gusl.gojserver.controller;

import com.gusl.common.common.BaseController;
import com.gusl.common.common.Result;
import com.gusl.gojserver.pojo.dto.ProblemDraftDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.service.ProblemService;
import com.gusl.gojserver.service.ProblemTestDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 用户题目草稿和测试数据上传接口。
 */
@Tag(name = "题目上传")
@RequiredArgsConstructor
@RestController
@RequestMapping("/upload")
public class ProblemUploadController extends BaseController {

    private final ProblemService problemService;

    private final ProblemTestDataService problemTestDataService;

    /**
     * 创建题目草稿、上传测试数据并提交审核。
     */
    @Operation(summary = "提交题目草稿")
    @PostMapping(value = "/upload-problem", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result uploadProblem(
            @RequestPart("draft") ProblemDraftDto draftDto,
            @RequestPart("file") MultipartFile data,
            @AuthenticationPrincipal LoginUser user
    ) throws IOException {
        problemService.uploadProblemByUser(draftDto, data, user);
        return success("题目已提交审核");
    }

    @Operation(summary = "更新被驳回题目的测试数据")
    @PostMapping(value = "/upload-update-testData/{problemId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result updateTestDataWithdraw(
            @PathVariable Long problemId,
            @RequestPart("file") MultipartFile data,
            @AuthenticationPrincipal LoginUser loginUser
    ) throws IOException {
        problemTestDataService.updateTestDataWithdraw(problemId, data, loginUser);
        return success();
    }

    /**
     * 用户重新提交上传问题
     */
    @Operation(summary = "重新上传被驳回题目")
    @PostMapping("/reUpload-problem/{problemId}")
    public Result reUploadProblem(
            @PathVariable Long problemId,
            @AuthenticationPrincipal LoginUser loginUser
    ){
        problemService.reUploadProblem(problemId, loginUser);
        return success();
    }
}
