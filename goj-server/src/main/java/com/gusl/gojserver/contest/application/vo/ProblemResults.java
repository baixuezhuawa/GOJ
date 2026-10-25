package com.gusl.gojserver.contest.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 比赛排行榜每行, 单个问题的状态.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProblemResults {

    @Schema(description = "是否通过")
    private boolean accepted;

    @Schema(description = "错误次数")
    private Integer wrongAttempts;

    @Schema(description = "通过时间")
    private Integer acceptedMinute;

}
