package com.gusl.gojjudge.sercice.testcase;


import com.gusl.common.constant.StorageType;
import com.gusl.gojjudge.exception.SystemErrorException;
import com.gusl.gojjudge.pojo.entity.TestDataRef;
import com.gusl.gojjudge.sercice.runner.LocalFileTestCaseReader;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 数据加载器
 */
@Component
@RequiredArgsConstructor
public class LocalFileTestCaseProvider implements TestCaseProvider {

    @Value("${goj.judge.data-root}")
    private String dataRoot;


    @Override
    public boolean supports(StorageType storageType) {
        return StorageType.LOCAL_FILE == storageType;
    }

    @Override
    public TestCaseReader open(TestDataRef ref){
        if(ref == null || ref.getStoragePath() == null){
            throw new SystemErrorException("测试数据引用不能为空");
        }

        if(ref.getTestCaseCount() == null || ref.getTestCaseCount() <= 0){
            throw new SystemErrorException("测试点数量异常");
        }

        Path rootPath = Path.of(dataRoot).toAbsolutePath().normalize();

        Path storagePath = rootPath.resolve(ref.getStoragePath()).normalize();

        if(!storagePath.startsWith(rootPath)){
            throw new SystemErrorException("测试数据路劲非法");
        }

       return new LocalFileTestCaseReader(storagePath, ref.getTestCaseCount());
    }

}
