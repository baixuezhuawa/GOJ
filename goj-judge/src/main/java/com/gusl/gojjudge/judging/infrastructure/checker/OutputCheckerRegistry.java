package com.gusl.gojjudge.judging.infrastructure.checker;

import com.gusl.gojjudge.judging.domain.checker.OutputChecker;

import com.gusl.common.constant.CheckType;
import com.gusl.gojjudge.judging.domain.exception.SystemErrorException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OutputCheckerRegistry {

    private final List<OutputChecker> outputCheckerList;

    public OutputChecker require(CheckType checkType){

        for(OutputChecker checker : outputCheckerList){
            if(checker.getCheckType() == checkType){
                return checker;
            }
        }

        throw new SystemErrorException("不受支持的检测类型: " + checkType);
    }

}
