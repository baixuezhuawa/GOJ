package com.gusl.gojserver.problem;

import com.gusl.common.pojo.entity.Problem;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gusl.common.pojo.entity.ProblemTestData;
import com.gusl.gojserver.problem.application.ProblemTestDataServiceImpl;
import com.gusl.gojserver.problem.infrastructure.persistence.ProblemMapper;
import com.gusl.gojserver.problem.infrastructure.persistence.ProblemTestDataMapper;
import com.gusl.gojserver.problem.infrastructure.storage.StoredTestData;
import com.gusl.gojserver.problem.infrastructure.storage.TestDataStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 测试管理员覆盖和删除规则，以及数据库失败后的文件处理。 */
class ProblemTestDataServiceTests {

    private ProblemMapper problems;
    private ProblemTestDataMapper datasets;
    private TestDataStorage storage;
    private TransactionTemplate transactions;
    private ProblemTestDataServiceImpl service;
    private final MockMultipartFile file = new MockMultipartFile("file", new byte[]{1});
    private final StoredTestData saved = new StoredTestData("a.zip", "new/data.zip", "new/cases", "sha", 2);

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ProblemTestData.class);
        problems = mock(ProblemMapper.class);
        datasets = mock(ProblemTestDataMapper.class);
        storage = mock(TestDataStorage.class);
        transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(call -> {
            TransactionCallback<?> callback = call.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        when(problems.selectOne(any())).thenReturn(new Problem());
        service = new ProblemTestDataServiceImpl(problems, datasets, storage, transactions);
    }

    @Test
    void overwriteKeepsIdAndActiveFlag() {
        ProblemTestData old = existing();
        when(datasets.selectOne(any())).thenReturn(old);
        when(storage.save(1L, 3, file)).thenReturn(saved);

        assertEquals(9L, service.saveTestData(1L, 3, file, "修改"));
        ArgumentCaptor<ProblemTestData> capture = ArgumentCaptor.forClass(ProblemTestData.class);
        verify(datasets).updateById(capture.capture());
        assertEquals(9L, capture.getValue().getId());
        assertTrue(capture.getValue().getActive());
        assertEquals("new/cases", capture.getValue().getStoragePath());
        assertEquals(3, capture.getValue().getVersion());
        verify(storage).remove("old/data.zip", "old/cases");
    }

    @Test
    void newVersionStartsInactiveWithAdministratorVersion() {
        when(storage.save(1L, 3, file)).thenReturn(saved);
        when(datasets.insert(any(ProblemTestData.class))).thenAnswer(call -> {
            ProblemTestData data = call.getArgument(0);
            data.setId(10L);
            return 1;
        });
        assertEquals(10L, service.saveTestData(1L, 3, file, null));
        ArgumentCaptor<ProblemTestData> capture = ArgumentCaptor.forClass(ProblemTestData.class);
        verify(datasets).insert(capture.capture());
        assertFalse(capture.getValue().getActive());
        assertEquals(3, capture.getValue().getVersion());
    }

    @Test
    void databaseFailureOnlyRemovesNewFiles() {
        when(storage.save(1L, 3, file)).thenReturn(saved);
        when(datasets.selectOne(any())).thenReturn(existing());
        when(datasets.updateById(any(ProblemTestData.class))).thenThrow(new IllegalStateException("数据库失败"));

        assertThrows(IllegalStateException.class, () -> service.saveTestData(1L, 3, file, null));
        verify(storage).remove("new/data.zip", "new/cases");
        verify(storage, never()).remove("old/data.zip", "old/cases");
    }

    @Test
    void administratorCanDeleteActiveVersion() {
        when(datasets.selectOne(any())).thenReturn(existing());
        service.deleteTestData(1L, 3);
        verify(datasets).deleteById(9L);
        verify(storage).remove("old/data.zip", "old/cases");
    }

    @Test
    void enablingVersionDisablesOthersAndSetsOnlyActiveFlag() {
        ProblemTestData data = existing();
        data.setTestNodeCount(2);
        when(datasets.selectOne(any())).thenReturn(data);
        service.setTestDataActive(1L, 3, true);
        verify(storage).requireReadable("old/cases", 2);
        ArgumentCaptor<LambdaUpdateWrapper<ProblemTestData>> capture = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(datasets, times(2)).update(capture.capture());
        assertTrue(capture.getAllValues().get(0).getParamNameValuePairs().containsValue(false));
        assertTrue(capture.getAllValues().get(1).getParamNameValuePairs().containsValue(true));
        assertFalse(capture.getValue().getSqlSet().contains("status"));
    }

    private ProblemTestData existing() {
        return ProblemTestData.builder().id(9L).problemId(1L).version(3).active(true)
                .archivePath("old/data.zip").storagePath("old/cases").build();
    }
}
