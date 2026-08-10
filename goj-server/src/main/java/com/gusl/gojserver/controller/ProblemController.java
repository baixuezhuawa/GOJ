package com.gusl.gojserver.controller;

import com.gusl.common.common.BaseController;
import com.gusl.common.common.Result;
import com.gusl.gojserver.pojo.dto.ProblemPageListDto;
import com.gusl.gojserver.pojo.vo.ProblemPageListVo;
import com.gusl.gojserver.service.ProblemService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "问题管理", description = "问题相关接口")
@RestController
@RequestMapping("/problem")
@RequiredArgsConstructor
public class ProblemController extends BaseController {

    private final ProblemService problemService;

    /**
     * 获取题目列表/筛选题目
     * @param dto 路径参数, 筛选条件
     * @return 简略题目列表
     */
    @GetMapping("/list")
    public Result problemList(@ModelAttribute ProblemPageListDto dto){
        List<ProblemPageListVo> records = problemService.getProblemList(dto);
        return success("操作成功", records);
    }

    @GetMapping("/{problemId}")
    public Result getProblemById(@PathVariable("problemId") Long id){
        return success("操作成功", problemService.getProblemInfoById(id));
    }
}
