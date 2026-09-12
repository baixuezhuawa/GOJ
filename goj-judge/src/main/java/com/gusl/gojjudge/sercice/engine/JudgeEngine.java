package com.gusl.gojjudge.sercice.engine;


import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.constant.SandBoxStatus;
import com.gusl.common.constant.SystemConstant;
import com.gusl.gojjudge.adapter.AbstractLanguageAdapter;
import com.gusl.gojjudge.adapter.LanguageAdapterRegistry;
import com.gusl.gojjudge.exception.JudgeSystemException;
import com.gusl.gojjudge.pojo.entity.CompileResult;
import com.gusl.gojjudge.pojo.entity.JudgeContext;
import com.gusl.gojjudge.pojo.entity.JudgeExecutionRequest;
import com.gusl.gojjudge.pojo.entity.JudgeResult;
import com.gusl.gojjudge.sercice.compiler.Compiler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 测评引擎
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeEngine {

    /** 语言适配器选择器 */
    private final LanguageAdapterRegistry languageAdapterRegistry;

    /** 测评策略 */
    private final List<JudgeStrategy> strategies;

    /** 编译器 */
    private final Compiler compiler;


    // 执行一次判题
    public void judge(JudgeExecutionRequest request, JudgeSession cur){
        // 选择语言适配器
        AbstractLanguageAdapter adapter = languageAdapterRegistry.require(request.getLanguage());

        // 选择判题策略
        JudgeStrategy strategy = strategies.stream()
                .filter(item -> item.executionMode() == request.getExecutionMode())
                .findFirst()
                .orElseThrow(() -> new JudgeSystemException("不支持的判题执行方式"));

        CompileResult compileResult = null;

        try {

            // 如果需要编译则, 修改当前为编译状态
            if(adapter.isNeedCompile()){
                cur.status(JudgingConstant.COMPILE).push();
            }

            // 编译
            compileResult = compiler.compile(request.getSourceCode(), adapter);

            cur.compilerMessage(compileResult.getCompileMsg()).push();

            // 编译失败也是正常的判题结果, 写回 CE 后结束
            if(!SandBoxStatus.ACCEPTED.equals(compileResult.getStatus())){
                cur.status(JudgingConstant.COMPILE_ERROR).push();
                return ;
            }

            // 进入运行阶段，同时保存编译信息
            cur.status(JudgingConstant.RUNNING).push();

            // 测评环境
            JudgeContext context = JudgeContext.builder()
                    .request(request)
                    .adapter(adapter)
                    .compileResult(compileResult)
                    .build();

            // 不同类型的题会有不同的判题策略, 运行交给不是的策略的实现类完成.
            JudgeResult result = strategy.judge(context);

            // 策略必须给终态, 不能作为普通结果落库
            if(!JudgingConstant.TERMINAL_STATUSES.contains(result.getStatus())){
                throw new JudgeSystemException("判题策略没有返回终态");
            }

            // 系统故障交给任务层, 不能作为普通结果落库
            if(SystemConstant.SYSTEM_ERROR.equals(result.getStatus())){
                throw new JudgeSystemException(result.getJudgeMsg());
            }

            // 甚至终态结果.
            cur.status(result.getStatus())
                    .usage(result.getTimeMs(), result.getMemoryKb())
                    .judgeMessage(result.getJudgeMsg())
                    .score(result.getScore())
                    .push();

        }finally {
            if(compileResult != null){
                try {
                    compiler.deleteCacheCompileFile(compileResult);
                }catch (Exception e){
                    log.warn("本次清理编译产物失败", e);
                }
            }

        }

    }


}
