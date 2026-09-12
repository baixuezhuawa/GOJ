package com.gusl.gojjudge.sercice.testcase;

import com.gusl.common.constant.StorageType;
import com.gusl.gojjudge.pojo.entity.TestDataRef;

public interface TestCaseProvider {


    /**
     * 当前 Provider 是否支持指定的存储类型。
     */
    boolean supports(StorageType storageType);

    /**
     * 创建一个测试数据读取器。
     */
    TestCaseReader open(TestDataRef ref);

}
