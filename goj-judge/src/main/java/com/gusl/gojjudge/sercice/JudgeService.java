package com.gusl.gojjudge.sercice;

import java.io.IOException;

/**
 * Judge 测评服务接口。
 */
public interface JudgeService {

    /**
     * 执行指定提交的测评任务。
     *
     * @param taskId 提交记录 ID
     * @throws IOException 读取本地测试数据失败时抛出
     */
    void judge(Long taskId) throws IOException;
}
