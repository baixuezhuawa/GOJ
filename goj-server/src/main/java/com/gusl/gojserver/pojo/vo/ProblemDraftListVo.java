package com.gusl.gojserver.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(name = "简单题目草稿信息")
public class ProblemDraftListVo {

    @Schema(description = "问题id")
    private Long id;

    @Schema(description = "问题名称")
    private String problemName;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "题目状态：0 草稿，1 已发布，2 已停用，3 待审核，4 已退回，5 准备中")
    private Integer status;
}
