package com.gusl.gojjudge.adapter;

import com.gusl.gojjudge.exception.SystemErrorException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LanguageAdapterRegistry {

    private final List<AbstractLanguageAdapter> adapterList;

    /**
     * 寻找语言适配器
     * @param languageCode 语言代码
     * @return 语言适配器
     */
    public AbstractLanguageAdapter require(String languageCode){
        for(AbstractLanguageAdapter adapter : adapterList){
            if(adapter.languageCode().equals(languageCode)){
                return adapter;
            }
        }
        throw new SystemErrorException("该语言暂不支持: " + languageCode);
    }

}
