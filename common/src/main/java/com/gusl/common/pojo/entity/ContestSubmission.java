package com.gusl.common.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.gusl.common.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Schema(name = "比赛提交")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ContestSubmission extends BaseEntity {

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

    /** 比赛id */
    @Schema(description = "比赛 id")
    private Long contestId;

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

    /** 源代码 sha256 */
    @Schema(description = "源代码的sha256值, 用于判断重复提交")
    @TableField("source_sha256")
    private String sourceSha256;

    /** 测评版本, 用于后续重测 */
    @Schema(description = "测评版本, 用于后续回滚重测")
    private Integer version;
}
