package com.gusl.gojserver.controller;

import com.gusl.common.common.BaseController;
import com.gusl.common.common.Result;
import com.gusl.common.constant.ProblemStatus;
import com.gusl.gojserver.config.properties.SubmissionProperties;
import com.gusl.gojserver.pojo.vo.ProblemStatusVo;
import com.gusl.gojserver.pojo.vo.ProblemTagsVo;
import com.gusl.gojserver.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "公共接口")
@RestController
@RequestMapping("/common")
@RequiredArgsConstructor
public class CommonController extends BaseController {

    private final SubmissionProperties submissionProperties;

    private final TagService tagService;

    @Operation(summary = "获取受支持的语言")
    @GetMapping("/supported-language")
    public Result getSupportedLanguage() {
        return success("操作成功", submissionProperties.getLanguages());
    }

    @Operation(summary = "获取题目数据库状态值")
    @GetMapping("/problem-status")
    public Result getProblemStatus(){
        List<ProblemStatusVo> statusList = List.of(
                new ProblemStatusVo(ProblemStatus.DRAFT, "草稿"),
                new ProblemStatusVo(ProblemStatus.PUBLISH, "已发布"),
                new ProblemStatusVo(ProblemStatus.DISABLE, "已停用"),
                new ProblemStatusVo(ProblemStatus.PENDING, "待审核"),
                new ProblemStatusVo(ProblemStatus.WITHDRAW, "已退回"),
                new ProblemStatusVo(ProblemStatus.PREPARE, "准备中")
        );
        return success("操作成功", statusList);
    }


    @Operation(summary = "获取关于问题的全部标签")
    @GetMapping("/problem-all-tags")
    public Result getAllTags(){
        List<ProblemTagsVo> voList = tagService.getAllTags();
        return success("操作成功", voList);
    }

}
