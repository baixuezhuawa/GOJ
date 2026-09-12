package com.gusl.gojjudge.sercice.testcase;

import com.gusl.gojjudge.pojo.entity.TestCase;

public interface TestCaseReader extends AutoCloseable{

    /** 返回测试点数量 */
    int count();

    /**
     * 按测试点读取测试数据
     * @param testCaseNo 测试点
     * @return 测试点
     */
    TestCase read(int testCaseNo);

    @Override
    void close();
}
