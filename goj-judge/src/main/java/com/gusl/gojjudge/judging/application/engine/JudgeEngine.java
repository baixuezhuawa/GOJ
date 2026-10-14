package com.gusl.gojjudge.judging.application.engine;

import com.gusl.gojjudge.judging.application.strategy.JudgeStrategy;


import com.gusl.common.constant.JudgingConstant;
import com.gusl.common.constant.CheckType;
import com.gusl.common.constant.SandBoxStatus;
import com.gusl.common.constant.SystemConstant;
import com.gusl.gojjudge.judging.infrastructure.language.AbstractLanguageAdapter;
import com.gusl.gojjudge.judging.infrastructure.language.LanguageAdapterRegistry;
import com.gusl.gojjudge.judging.domain.exception.JudgeSystemException;
import com.gusl.gojjudge.judging.infrastructure.compiler.CompileResult;
import com.gusl.gojjudge.judging.domain.model.JudgeResult;
import com.gusl.gojjudge.judging.infrastructure.compiler.Compiler;
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
        CompileResult checkerCompileResult = null;

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

            // 特殊题复用编译模块准备校验程序，全部测试点共用编译产物。
            AbstractLanguageAdapter checkerAdapter = null;
            if (CheckType.SPECIAL.equals(request.getCheckerConfig().getCheckType())) {
                checkerAdapter = languageAdapterRegistry.require(request.getCheckerConfig().getLanguage());

                checkerCompileResult = compiler.compile(request.getCheckerConfig().getSourceCode(), checkerAdapter);

                if (!SandBoxStatus.ACCEPTED.equals(checkerCompileResult.getStatus())) {
                    throw new JudgeSystemException("校验程序编译失败: " + checkerCompileResult.getCompileMsg());
                }
            }

            // 进入运行阶段，同时保存编译信息
            cur.status(JudgingConstant.RUNNING).push();

            // 测评环境
            JudgeContext context = JudgeContext.builder()
                    .request(request)
                    .adapter(adapter)
                    .compileResult(compileResult)
                    .checkerAdapter(checkerAdapter)
                    .checkerCompileResult(checkerCompileResult)
                    .build();

            // 执行方式决定策略；普通题与答案不唯一题共用批量测试点策略。
            JudgeResult result = strategy.judge(context);

            // 策略必须给终态, 不能作为普通结果落库
            if(!JudgingConstant.TERMINAL_STATUSES.contains(result.getStatus())){
                throw new JudgeSystemException("判题策略没有返回终态");
            }

            // 系统故障交给任务层, 不能作为普通结果落库
            if(SystemConstant.SYSTEM_ERROR.equals(result.getStatus())){
                throw new JudgeSystemException(result.getJudgeMsg());
            }

            // 设置终态结果。
            cur.status(result.getStatus())
                    .usage(result.getTimeMs(), result.getMemoryKb())
                    .judgeMessage(result.getJudgeMsg())
                    .score(result.getScore())
                    .push();

        }finally {
            // 校验程序和选手程序分别清理，避免一个清理失败阻止另一个。
            if (checkerCompileResult != null) {
                try {
                    compiler.deleteCacheCompileFile(checkerCompileResult);
                } catch (Exception e) {
                    log.warn("本次清理校验程序编译产物失败", e);
                }
            }
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
