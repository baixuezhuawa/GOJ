package com.gusl.common.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gusl.common.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户提交实体，保存源代码和评测结果摘要。
 *
 * <p>评测状态由 API 服务和 Judge Worker 共同推进，源代码不应在 Controller
 * 中直接执行。</p>
 */
@Tag(name = "提交实体")
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("submission")
public class Submission extends BaseEntity {

    /** 主键 id。 */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键 id")
    private Long id;

    /** 提交用户 id。 */
    @Schema(description = "用户 id")
    private Long userId;

    /** 题目 id。 */
    @Schema(description = "题目 id")
    private Long problemId;

    /** 编程语言编码，例如 JAVA。 */
    @Schema(description = "编程语言")
    private String language;

    /** 用户提交的源代码。 */
    @Schema(description = "源代码")
    private String sourceCode;

    /** 评测状态：QUEUED、JUDGING、AC、WA、CE、RE、TLE、MLE、SYSTEM_ERROR。 */
    @Schema(description = "评测状态")
    private String status;

    /** 评测得分。 */
    @Schema(description = "评测得分")
    private Integer score;

    /** 运行耗时，单位为毫秒。 */
    @Schema(description = "运行耗时/ms")
    private Integer timeMs;

    /** 运行期间的最大内存，单位为 KB。 */
    @Schema(description = "运行内存/KB")
    private Integer memoryKb;

    /** 编译器输出信息。 */
    @Schema(description = "编译信息")
    private String compilerMsg;

    /** 评测结果或运行诊断信息。 */
    @Schema(description = "评测信息")
    private String judgeMsg;

    /** API 接收提交的时间。 */
    @Schema(description = "提交时间")
    private LocalDateTime submissionTime;

    /** Judge Worker 开始处理的时间。 */
    @Schema(description = "评测开始时间")
    private LocalDateTime judgeStartTime;

    /** 评测完成的时间。 */
    @Schema(description = "评测结束时间")
    private LocalDateTime judgeEndTime;
}
