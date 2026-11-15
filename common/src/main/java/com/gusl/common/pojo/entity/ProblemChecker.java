package com.gusl.common.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gusl.common.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 题目校验程序，保存一道题当前使用的语言和源码。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("problem_checker")
public class ProblemChecker extends BaseEntity {

    /** 校验程序 id。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属题目 id，一道题只保存一份当前校验程序。 */
    private Long problemId;

    /** 校验程序语言编码，与语言适配器配置一致。 */
    private String language;

    /** 校验程序源码：读取选手输出，输出用于最终比对的文本。 */
    private String sourceCode;
}
