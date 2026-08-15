package com.gusl.gojserver.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员审核时可见的测试数据摘要。
 */
@Schema(name = "审核测试数据摘要")
@Data
public class ProblemTestDataReviewVo {

    @Schema(description = "测试数据集 id")
    private Long id;

    @Schema(description = "原始压缩包名称")
    private String archiveName;

    @Schema(description = "压缩包 SHA-256")
    private String archiveSha256;

    @Schema(description = "测试点数量")
    private Integer testNodeCount;

    @Schema(description = "测试数据状态")
    private String status;

    @Schema(description = "上传时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
