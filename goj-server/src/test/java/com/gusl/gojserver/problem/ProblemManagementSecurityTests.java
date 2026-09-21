package com.gusl.gojserver.problem;

import com.gusl.gojserver.problem.application.ProblemReviewJudgeService;
import com.gusl.gojserver.problem.application.ProblemService;
import com.gusl.gojserver.problem.application.ProblemTestDataService;
import com.gusl.gojserver.problem.application.dto.ProblemSaveDto;
import com.gusl.gojserver.problem.application.dto.ProblemReviewJudgeDto;
import com.gusl.gojserver.problem.interfaces.web.AdminProblemController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 通过真实 Spring 方法鉴权代理验证管理员边界，无需连接数据库。 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProblemManagementSecurityTests.Config.class)
class ProblemManagementSecurityTests {

    @Autowired
    private AdminProblemController controller;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        AdminProblemController controller() {
            ProblemService problems = mock(ProblemService.class);
            when(problems.createProblem(any(), any())).thenReturn(1L);
            ProblemTestDataService data = mock(ProblemTestDataService.class);
            when(data.getArchive(1L, 1)).thenReturn(new ByteArrayResource(new byte[]{1}));
            return new AdminProblemController(problems, mock(ProblemReviewJudgeService.class), data);
        }
    }

    @Test
    @WithMockUser(roles = "USER")
    void ordinaryUserCannotManageProblemOrData() {
        for (Executable action : managementActions()) {
            assertThrows(AccessDeniedException.class, action);
        }
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void administratorCanUseAllManagementActions() {
        for (Executable action : managementActions()) {
            assertDoesNotThrow(action);
        }
    }

    private List<Executable> managementActions() {
        return List.of(
                () -> controller.createProblem(new ProblemSaveDto(), null),
                () -> controller.updateProblem(1L, new ProblemSaveDto()),
                () -> controller.setProblemStatus(1L, 1),
                () -> controller.saveTestData(1L, 1, null, null),
                () -> controller.setTestDataActive(1L, 1, true),
                () -> controller.deleteTestData(1L, 1),
                () -> controller.downloadTestData(1L, 1),
                () -> controller.submitJudge(1L, new ProblemReviewJudgeDto(), null)
        );
    }
}
