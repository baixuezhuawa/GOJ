package com.gusl.gojserver.problem.interfaces.web;

import com.gusl.common.common.BaseController;
import com.gusl.common.common.PageResult;
import com.gusl.common.common.Result;
import com.gusl.gojserver.problem.application.dto.ProblemPageListDto;
import com.gusl.gojserver.account.infrastructure.security.LoginUser;
import com.gusl.gojserver.problem.application.vo.ProblemPageListVo;
import com.gusl.gojserver.problem.application.ProblemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

}
