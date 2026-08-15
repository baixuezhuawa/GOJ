package com.gusl.gojserver.controller;

import com.gusl.common.common.BaseController;
import com.gusl.common.common.Result;
import com.gusl.gojserver.pojo.dto.SubmissionDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.pojo.vo.SubmissionVo;
import com.gusl.gojserver.service.SubmissionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "提交管理")
@RestController
@RequestMapping("/submission")
@RequiredArgsConstructor
public class SubmissionController extends BaseController {

    private final SubmissionService submissionService;

    /**
     * 万恶的源头啊!!!
     * @param submissionDto 用户提交
     * @return 是否提交成功
     */
    @PostMapping("/submit")
    public Result submission(@RequestBody SubmissionDto submissionDto, @AuthenticationPrincipal LoginUser loginUser) {
        submissionService.submitProblemToJudge(submissionDto, loginUser);
        return success("hhh~~ 你肯定AC了, 看下一题吧!");
    }

    @GetMapping("/submission/{submissionId}")
    public Result submitStatus(@PathVariable Long submissionId){
           SubmissionVo vo = submissionService.getSubmissionById(submissionId);
           return success("操作成功", vo);
    }
}
