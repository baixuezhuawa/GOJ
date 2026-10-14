package com.gusl.gojjudge.judging.domain.model;

import com.gusl.common.constant.CheckType;
import lombok.Data;

/** 输出校验配置，保存校验方式及特殊校验程序的当前源码。 */
@Data
public class CheckerConfig {

    /** 检查类型 */
    private CheckType checkType;

    /** 校验程序语言，仅特殊校验使用。 */
    private String language;

    /** 加载请求时读取的当前校验代码，仅特殊校验使用。 */
    private String sourceCode;

}
