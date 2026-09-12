package com.gusl.gojjudge.sercice.runner;


import com.gusl.gojjudge.exception.SystemErrorException;
import com.gusl.gojjudge.pojo.entity.TestCase;
import com.gusl.gojjudge.sercice.testcase.TestCaseReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalFileTestCaseReader implements TestCaseReader {

    private final Path storagePath;

    private final int testCaseCount;

    public LocalFileTestCaseReader(Path storagePath, int testCaseCount){
        this.storagePath = storagePath;
        this.testCaseCount = testCaseCount;
    }

    /**
     * @return 测试点数量
     */
    @Override
    public int count() {
        return testCaseCount;
    }

    /**
     * 读取输入输出数据
     * @param testCaseNo 测试点
     * @return 测试点数据
     */
    @Override
    public TestCase read(int testCaseNo) {
        if (testCaseNo < 1 || testCaseNo > testCaseCount) {
            throw new IllegalArgumentException("测试点编号非法：" + testCaseNo);
        }

        Path testCasePath = storagePath
                .resolve("test" + testCaseNo)
                .normalize();

        Path inputPath = testCasePath.resolve("input.txt");
        Path outputPath = testCasePath.resolve("output.txt");

        String input = null;
        String expectedOutput = null;
        try {
            input = Files.readString(inputPath);
            expectedOutput = Files.readString(outputPath);
        } catch (IOException e) {
            throw new SystemErrorException("测试数据异常 input:" + inputPath + " output:" + outputPath + "\n" + e);
        }

        return new TestCase(testCaseNo, input, expectedOutput);
    }


    @Override
    public void close() {

    }
}
