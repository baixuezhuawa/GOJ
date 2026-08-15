package com.gusl.gojserver.service.support;

import com.gusl.common.common.BaseException;
import com.gusl.common.utils.Sha256Utils;
import com.gusl.common.utils.StringUtils;
import com.gusl.gojserver.config.properties.SubmissionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Judge 源码请求公共校验器，负责校验源码长度和语言编码并计算摘要。
 */
@Component
@RequiredArgsConstructor
public class JudgeSourceValidator {

    private final SubmissionProperties submissionProperties;

    /**
     * 校验语言和源码，并计算源码 SHA-256。
     *
     * @param language 语言编码
     * @param sourceCode 源代码
     * @return 源代码 SHA-256
     */
    public String validateAndHash(String language, String sourceCode) {
        // 第一步：源码不能为空，并且不能超过服务端配置的长度上限。
        if (StringUtils.isEmpty(sourceCode)) {
            throw new BaseException("代码不能为空");
        }
        if (sourceCode.length() > submissionProperties.getMaxCodeLength()) {
            throw new BaseException("代码长度超出最大限制");
        }

        // 第二步：语言必须存在于 server 对外开放的语言列表中，并处于启用状态。
        boolean supported = submissionProperties.getLanguages().stream()
                .anyMatch(languageConfig ->
                        languageConfig.isEnabled() && languageConfig.getCode().equals(language)
                );
        if (!supported) {
            throw new BaseException("不是可支持的语言类型");
        }

        // 第三步：计算摘要，供不同提交业务分别执行重复提交校验。
        return Sha256Utils.sha256Hex(sourceCode);
    }
}
