package com.gusl.gojserver.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待审核题目列表项。
 */
@Schema(name = "待审核题目列表项")
@Data
public class AdminProblemReviewListVo {

    @Schema(description = "题目 id")
    private Long problemId;

    @Schema(description = "题目名称")
    private String problemName;

    @Schema(description = "作者 id")
    private Long userId;

    @Schema(description = "作者用户名")
    private String username;

    @Schema(description = "提交审核时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
