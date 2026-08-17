package com.gusl.common.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gusl.common.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户语言统计实体，记录用户使用每种语言的提交数量和通过数量。
 */
@TableName("user_language_stat")
@EqualsAndHashCode(callSuper = true)
@Data
public class UserLanguageStat extends BaseEntity {

    /** 主键 id。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户 id。 */
    private Long userId;

    /** 提交语言编码。 */
    private String language;

    /** 使用该语言完成测评的提交数量。 */
    private Long submissionCount;

    /** 使用该语言获得 Accepted 的提交数量。 */
    private Long acceptedCount;
}
