package com.gusl.gojserver.pojo.dto;

import com.gusl.common.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Schema(name = "提交搜索条件", description = "用于条件搜索提交列表")
@Data
public class SubmissionSearchDto extends PageQuery {

    private Long problemId;

    private String language;

    private String status;

}
