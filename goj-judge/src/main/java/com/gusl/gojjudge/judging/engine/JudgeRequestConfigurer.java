package com.gusl.gojjudge.judging.engine;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.constant.CheckType;
import com.gusl.common.constant.ProblemType;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.common.pojo.entity.ProblemChecker;
import com.gusl.gojjudge.judging.exception.JudgeSystemException;
import com.gusl.gojjudge.judging.model.CheckerConfig;
import com.gusl.gojjudge.judging.mapper.ProblemCheckerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 判题请求配置器，供各提交入口按题目类型装配执行方式和校验程序。 */
@Component
@RequiredArgsConstructor
public class JudgeRequestConfigurer {

    private final ProblemCheckerMapper problemCheckerMapper;

    /** 根据题目类型配置请求；特殊题读取数据库中的当前校验程序。 */
    public void configure(Problem problem, JudgeExecutionRequest request) {
        CheckerConfig config = new CheckerConfig();

        // 普通题直接比对；特殊题补充校验程序的语言和源码。
        if (ProblemType.NORMAL.equals(problem.getProblemType())) {
            config.setCheckType(CheckType.TEXT);
        } else if (ProblemType.SPECIAL.equals(problem.getProblemType())) {
            ProblemChecker checker = problemCheckerMapper.selectOne(
                    Wrappers.<ProblemChecker>lambdaQuery().eq(ProblemChecker::getProblemId, problem.getId()));
            if (checker == null) {
                throw new JudgeSystemException("题目未配置校验程序: " + problem.getId());
            }
            config.setCheckType(CheckType.SPECIAL);
            config.setLanguage(checker.getLanguage());
            config.setSourceCode(checker.getSourceCode());
        } else if (ProblemType.INTERACTION.equals(problem.getProblemType())) {
            // 交互题由 Engine 统一给出未支持提示。
        } else {
            throw new JudgeSystemException("不支持的题目类型: " + problem.getProblemType());
        }

        // Engine 按题目类型直接选择普通方法。
        request.setProblemType(problem.getProblemType());
        request.setCheckerConfig(config);
    }
}
