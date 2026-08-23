package com.gusl.gojserver.controller;

import com.gusl.common.common.BaseController;
import com.gusl.common.common.Result;
import com.gusl.gojserver.config.properties.SubmissionProperties;
import com.gusl.gojserver.config.properties.SysProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "公共接口")
@RestController
@RequestMapping("/common")
@RequiredArgsConstructor
public class CommonController extends BaseController {

    private final SubmissionProperties submissionProperties;

    @Operation(summary = "获取受支持的语言")
    @GetMapping("/supported-language")
    public Result getSupportedLanguage() {
        return success("操作成功", submissionProperties.getLanguages());
    }

}
