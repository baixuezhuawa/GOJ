package com.gusl.gojserver.controller;

import com.gusl.common.common.BaseController;
import com.gusl.common.common.Result;
import com.gusl.gojserver.pojo.dto.Submission2JudgeDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.pojo.vo.SubmissionDetailVo;
import com.gusl.gojserver.pojo.vo.SubmissionListVo;
import com.gusl.gojserver.pojo.vo.SubmissionVo;
import com.gusl.gojserver.service.SubmissionService;
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


    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "万恶源头")
    @PostMapping("/submit")
    public Result submission(@RequestBody Submission2JudgeDto submission2JudgeDto, @AuthenticationPrincipal LoginUser loginUser) {
        SubmissionVo submissionVo = submissionService.submitProblemToJudge(submission2JudgeDto, loginUser);
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
