package com.gusl.gojjudge.sercice.engine;

import com.gusl.gojjudge.exception.JudgeSystemException;
import com.gusl.gojjudge.sercice.submission.SubmissionJudgeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class HandlerRegistry {

    private final List<SubmissionJudgeHandler> handlerList;

    /**
     * 选择对应类型任务的处理器
     * @param taskType 任务类型
     * @return 任务处理器
     */
    public SubmissionJudgeHandler require(String taskType){

        for(SubmissionJudgeHandler handler : handlerList){
            if(handler.handlerTaskType().equals(taskType)){
                return handler;
            }
        }

        throw new JudgeSystemException("不受支持的测评类型: " + taskType);
    }

}
