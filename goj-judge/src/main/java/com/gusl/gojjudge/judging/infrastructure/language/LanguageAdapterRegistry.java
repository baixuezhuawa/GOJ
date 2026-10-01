package com.gusl.gojjudge.judging.infrastructure.language;

import com.gusl.gojjudge.judging.domain.exception.SystemErrorException;
import com.gusl.gojjudge.judging.infrastructure.language.properties.LanguageConfig;
import com.gusl.gojjudge.judging.infrastructure.language.properties.LanguageProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 语言适配器注册表，根据配置创建实例并按语言编码提供查询。
 */
@Component
public class LanguageAdapterRegistry {

    /** 整体替换只读注册表，保证查询线程能看到最新的完整快照。 */
    private volatile Map<String, AbstractLanguageAdapter> adapters = Map.of();

    /** 启动时由 Spring 传入配置，为每种语言创建通用适配器。 */
    public LanguageAdapterRegistry(LanguageProperties properties){
        reload(properties.getLanguages());
    }

    /** 全部配置校验成功后替换注册表；失败保留旧表，旧任务继续持有旧适配器。 */
    public synchronized void reload(List<LanguageConfig> languages){
        Assert.notNull(languages, "语言配置集合不能为空");
        Map<String, AbstractLanguageAdapter> next = new HashMap<>();

        // 先在局部构建完整注册表，不在现有表上增删或修改配置。
        for(LanguageConfig config : languages){
            AbstractLanguageAdapter adapter = new ConfigurableLanguageAdapter(config);
            Assert.isTrue(next.putIfAbsent(adapter.languageCode(), adapter) == null,
                    "语言编码重复: " + adapter.languageCode());
        }

        // 发布新快照；空集合表示移除全部语言，已经取得的适配器仍然有效。
        adapters = Map.copyOf(next);
    }

    /**
     * 寻找语言适配器
     * @param languageCode 语言代码
     * @return 语言适配器
     */
    public AbstractLanguageAdapter require(String languageCode){
        AbstractLanguageAdapter adapter = adapters.get(languageCode);
        if(adapter != null){
            return adapter;
        }
        throw new SystemErrorException("该语言暂不支持: " + languageCode);
    }

}
