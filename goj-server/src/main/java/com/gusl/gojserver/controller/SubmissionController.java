package com.gusl.gojserver.controller;

import com.gusl.common.common.BaseController;
import com.gusl.common.common.Result;
import com.gusl.gojserver.pojo.dto.Submission2JudgeDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.pojo.vo.SubmissionDetailVo;
import com.gusl.gojserver.pojo.vo.SubmissionListVo;
import com.gusl.gojserver.service.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "提交管理")
@RestController
@RequestMapping("/submission")
@RequiredArgsConstructor
public class SubmissionController extends BaseController {

    private final SubmissionService submissionService;


    @Operation(summary = "万恶源头")
    @PostMapping("/submit")
    public Result submission(@RequestBody Submission2JudgeDto submission2JudgeDto, @AuthenticationPrincipal LoginUser loginUser) {
        Long submissionId = submissionService.submitProblemToJudge(submission2JudgeDto, loginUser);
        return success("你肯定会AC的, 直接看下一题吧! 哈哈哈", submissionId);
    }

    @Operation(summary = "根据提交id查看提交信息")
    @GetMapping("/submission/{submissionId}")
    public Result submitStatus(@PathVariable Long submissionId) {
        SubmissionDetailVo vo = submissionService.getSubmissionById(submissionId);
        return success("操作成功", vo);
    }

}
