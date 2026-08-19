package com.gusl.gojserver.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.common.pojo.entity.UserLanguageStat;
import com.gusl.gojserver.config.properties.SubmissionProperties;
import com.gusl.gojserver.config.properties.entity.SupportedLanguageProperties;
import com.gusl.gojserver.mapper.UserLanguageStatMapper;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.pojo.vo.LanguageStatVo;
import com.gusl.gojserver.service.UserLanguageStatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户语言统计服务实现，读取派生统计表并补充语言显示名称。
 */
@Service
@RequiredArgsConstructor
public class UserLanguageStatServiceImpl
        extends ServiceImpl<UserLanguageStatMapper, UserLanguageStat>
        implements UserLanguageStatService {

    private final UserLanguageStatMapper userLanguageStatMapper;

    private final SubmissionProperties submissionProperties;

    /**
     * 获取当前用户使用过的语言统计，按照提交数量从高到低返回。
     *
     * @param loginUser 当前登录用户
     * @return 语言使用统计列表
     */
    @Override
    public List<LanguageStatVo> getMyLanguageStat(LoginUser loginUser) {
        List<UserLanguageStat> languageStats = userLanguageStatMapper.selectList(
                Wrappers.<UserLanguageStat>lambdaQuery()
                        .eq(UserLanguageStat::getUserId, loginUser.getUserId())
                        .orderByDesc(UserLanguageStat::getSubmissionCount)
                        .orderByAsc(UserLanguageStat::getLanguage)
        );

        // 把配置中的语言编码转换成用户可见名称。
        Map<String, String> displayNameMap = new HashMap<>();
        for (SupportedLanguageProperties language : submissionProperties.getLanguages()) {
            displayNameMap.put(language.getCode(), language.getDisplayName());
        }

        // 只返回当前用户实际使用过的语言。
        List<LanguageStatVo> result = new ArrayList<>();
        for (UserLanguageStat languageStat : languageStats) {
            result.add(new LanguageStatVo(
                    languageStat.getLanguage(),
                    displayNameMap.get(languageStat.getLanguage()),
                    languageStat.getSubmissionCount(),
                    languageStat.getAcceptedCount()
            ));
        }
        return result;
    }
}
