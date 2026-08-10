package com.gusl.gojserver.pojo.vo;

import com.gusl.common.pojo.entity.Problem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Schema(name = "问题详细信息vo")
@Data
public class ProblemInfoVo extends Problem {

    @Schema(description = "题目标签")
    private List<String> tags;

}
