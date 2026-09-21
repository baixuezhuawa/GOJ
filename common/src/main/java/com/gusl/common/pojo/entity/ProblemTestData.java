package com.gusl.common.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gusl.common.common.BaseEntity;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 题目测试数据集元信息。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("problem_test_data")
public class ProblemTestData extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long problemId;

    private Integer version;

    private Integer testNodeCount;

    private String archiveName;

    /** 原始 ZIP 相对于数据根目录的路径。 */
    private String archivePath;

    /** 解压后的测试点目录，相对于数据根目录。 */
    private String storagePath;

    private String archiveSha256;

    private Boolean active;
}
