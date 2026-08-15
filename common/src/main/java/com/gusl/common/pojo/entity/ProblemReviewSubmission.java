package com.gusl.common.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gusl.common.common.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 管理员验题提交实体，独立保存待审核题目的验题代码和测评结果。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("problem_review_submission")
public class ProblemReviewSubmission extends BaseEntity {

    /** 主键 id。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 待审核题目 id。 */
    private Long problemId;

    /** 本次验题使用的测试数据集 id。 */
    private Long problemTestDataId;

    /** 执行验题的管理员 id。 */
    private Long reviewerId;

    /** 编程语言编码。 */
    private String language;

    /** 管理员提交的验题代码。 */
    private String sourceCode;

    /** 测评状态。 */
    private String status;

    /** 测评得分。 */
    private Integer score;

    /** 最大运行耗时，单位为毫秒。 */
    private Integer timeMs;

    /** 最大运行内存，单位为 KB。 */
    private Integer memoryKb;

    /** 编译器输出信息。 */
    private String compilerMsg;

    /** 测评或运行信息。 */
    private String judgeMsg;

    /** 验题任务提交时间。 */
    private LocalDateTime submissionTime;

    /** Judge Worker 开始处理时间。 */
    private LocalDateTime judgeStartTime;

    /** Judge Worker 结束处理时间。 */
    private LocalDateTime judgeEndTime;

    /** 源代码 SHA-256。 */
    @TableField("source_sha256")
    private String sourceSha256;
}
