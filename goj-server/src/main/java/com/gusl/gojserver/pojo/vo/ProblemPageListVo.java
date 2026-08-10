package com.gusl.gojserver.pojo.vo;

import com.gusl.gojserver.pojo.entity.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(name = "题目列表条目", description = "每个题目的基本信息")
@Data
public class ProblemPageListVo {

    @Schema(description = "题目id")
    private Long problemId;

    @Schema(description = "题目")
    private String problemName;

    @Schema(description = "难度")
    private Integer difficulty;

    @Schema(description = "标签集合")
    private List<String> tags;

    @Schema(description = "是否解决")
    private boolean solveByMe;

}
