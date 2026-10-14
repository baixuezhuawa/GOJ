package com.gusl.gojserver.problem;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.gusl.common.common.BaseException;
import com.gusl.common.constant.ProblemStatus;
import com.gusl.common.constant.ProblemType;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.common.pojo.entity.ProblemTestData;
import com.gusl.gojserver.account.infrastructure.persistence.UserMapper;
import com.gusl.gojserver.account.infrastructure.security.LoginUser;
import com.gusl.gojserver.problem.application.ProblemServiceImpl;
import com.gusl.gojserver.problem.application.ProblemTestDataService;
import com.gusl.gojserver.problem.application.dto.ProblemSaveDto;
import com.gusl.gojserver.problem.infrastructure.persistence.ProblemMapper;
import com.gusl.gojserver.problem.infrastructure.persistence.ProblemTestDataMapper;
import com.gusl.gojserver.problem.infrastructure.persistence.TagMapper;
import com.gusl.gojserver.shared.application.pagination.PageFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 测试创建题目不依赖数据上传，以及发布时检查启用数据。 */
class ProblemManagementTests {

    private ProblemMapper problems;
    private ProblemTestDataService data;
    private ProblemServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Problem.class);
        problems = mock(ProblemMapper.class);
        data = mock(ProblemTestDataService.class);
        service = new ProblemServiceImpl(problems, mock(TagMapper.class), data, mock(PageFactory.class),
                mock(UserMapper.class), mock(ProblemTestDataMapper.class));
    }

    @Test
    void createsDraftWithoutDataAndDoesNotEnterReview() {
        ProblemSaveDto dto = new ProblemSaveDto();
        dto.setProblemName("测试题目");
        dto.setDescription("题面");
        dto.setInputDescription("输入");
        dto.setOutPutDescription("输出");
        dto.setTimeLimit(1000);
        dto.setMemoryLimit(65536);
        LoginUser user = mock(LoginUser.class);
        when(user.getUserId()).thenReturn(1L);
        when(problems.insert(any(Problem.class))).thenAnswer(call -> {
            Problem problem = call.getArgument(0);
            problem.setId(12L);
            return 1;
        });

        assertEquals(12L, service.createProblem(dto, user));
        ArgumentCaptor<Problem> capture = ArgumentCaptor.forClass(Problem.class);
        verify(problems).insert(capture.capture());
        assertEquals(ProblemStatus.DRAFT, capture.getValue().getStatus());
        assertEquals(ProblemType.NORMAL, capture.getValue().getProblemType());
        verifyNoInteractions(data);
    }

    @Test
    void publishingRequiresUsableActiveData() {
        when(problems.selectOne(any())).thenReturn(new Problem());
        when(data.requireActiveTestData(1L)).thenThrow(new BaseException("没有数据"));
        assertThrows(BaseException.class, () -> service.setProblemStatus(1L, ProblemStatus.PUBLISH));
        verify(problems, never()).update(any());
        doReturn(new ProblemTestData()).when(data).requireActiveTestData(1L);
        service.setProblemStatus(1L, ProblemStatus.PUBLISH);
        verify(problems).update(any());
    }

    @Test
    void administratorCanSetDraftOrDisabledWithoutData() {
        when(problems.selectOne(any())).thenReturn(new Problem());
        service.setProblemStatus(1L, ProblemStatus.DRAFT);
        service.setProblemStatus(1L, ProblemStatus.DISABLE);
        verifyNoInteractions(data);
    }
}
