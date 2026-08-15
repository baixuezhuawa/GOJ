package com.gusl.gojserver;

import com.gusl.common.pojo.entity.ProblemTestData;
import com.gusl.common.constant.ProblemTestDataStatus;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
class GojServerApplicationTests {

    @Test
    void contextLoads() {
        ProblemTestData testData = ProblemTestData.builder()
                .problemId(7L)
                .archiveName("nm")
                .status(ProblemTestDataStatus.UPLOADING)
                .active(false)
                .build();
        log.info(testData.toString());
    }

}
