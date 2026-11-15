package com.gusl.gojjudge.judging;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.gusl.common.constant.CheckType;
import com.gusl.common.constant.ProblemType;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.common.pojo.entity.ProblemChecker;
import com.gusl.gojjudge.judging.engine.JudgeExecutionRequest;
import com.gusl.gojjudge.judging.engine.JudgeRequestConfigurer;
import com.gusl.gojjudge.judging.exception.JudgeSystemException;
import com.gusl.gojjudge.judging.mapper.ProblemCheckerMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 验证题目类型路由、当前校验代码加载和缺失配置的失败边界。 */
class JudgeRequestConfigurerTests {

    @Test
    void normalProblemDoesNotLoadChecker() {
        ProblemCheckerMapper mapper = mock(ProblemCheckerMapper.class);
        JudgeExecutionRequest request = new JudgeExecutionRequest();
        new JudgeRequestConfigurer(mapper).configure(problem(ProblemType.NORMAL), request);

        assertEquals(ProblemType.NORMAL, request.getProblemType());
        assertEquals(CheckType.TEXT, request.getCheckerConfig().getCheckType());
        verifyNoInteractions(mapper);
    }

    @Test
    void specialProblemLoadsCurrentCheckerOnEachRequest() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ProblemChecker.class);
        ProblemCheckerMapper mapper = mock(ProblemCheckerMapper.class);
        ProblemChecker checker = new ProblemChecker();
        checker.setLanguage("py3");
        checker.setSourceCode("旧代码");
        when(mapper.selectOne(any())).thenReturn(checker);
        JudgeRequestConfigurer configurer = new JudgeRequestConfigurer(mapper);
        JudgeExecutionRequest first = new JudgeExecutionRequest();
        configurer.configure(problem(ProblemType.SPECIAL), first);

        checker.setSourceCode("当前代码");
        JudgeExecutionRequest second = new JudgeExecutionRequest();
        configurer.configure(problem(ProblemType.SPECIAL), second);

        assertEquals(CheckType.SPECIAL, second.getCheckerConfig().getCheckType());
        assertEquals(ProblemType.SPECIAL, second.getProblemType());
        assertEquals("py3", second.getCheckerConfig().getLanguage());
        assertEquals("旧代码", first.getCheckerConfig().getSourceCode());
        assertEquals("当前代码", second.getCheckerConfig().getSourceCode());
        verify(mapper, times(2)).selectOne(any());
    }

    @Test
    void missingCheckerFailsAndInteractiveTypeReachesEngine() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ProblemChecker.class);
        JudgeRequestConfigurer configurer = new JudgeRequestConfigurer(mock(ProblemCheckerMapper.class));

        assertTrue(assertThrows(JudgeSystemException.class,
                () -> configurer.configure(problem(ProblemType.SPECIAL), new JudgeExecutionRequest()))
                .getMessage().contains("未配置校验程序"));
        JudgeExecutionRequest interaction = new JudgeExecutionRequest();
        configurer.configure(problem(ProblemType.INTERACTION), interaction);
        assertEquals(ProblemType.INTERACTION, interaction.getProblemType());
    }

    /** 创建只包含请求装配所需字段的题目。 */
    private Problem problem(Integer type) {
        Problem problem = new Problem();
        problem.setId(7L);
        problem.setProblemType(type);
        return problem;
    }
}
