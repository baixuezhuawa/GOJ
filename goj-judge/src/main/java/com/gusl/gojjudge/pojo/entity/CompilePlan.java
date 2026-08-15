package com.gusl.gojjudge.pojo.entity;

import com.alibaba.fastjson2.JSONObject;
import lombok.Builder;
import lombok.Data;

/**
 * 编译阶段计划。
 *
 * <p>语言适配器通过该对象把“是否需要编译、如何请求沙箱、编译后缓存什么文件”
 * 传递给 JudgeService。该对象不执行编译，只保存编排所需的数据。</p>
 */
@Data
@Builder
public class CompilePlan {

    /** 当前语言是否需要独立编译阶段。 */
    boolean required;

    /** 发给 go-judge {@code /run} 接口的编译请求体。 */
    JSONObject requestBody;

    /** 编译请求通过 {@code copyOutCached} 生成的产物名称，例如 {@code Main.jar}。 */
    String cachedArtifactName;
}
