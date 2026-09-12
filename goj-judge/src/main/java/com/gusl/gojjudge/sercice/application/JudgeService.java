package com.gusl.gojjudge.sercice.application;

import com.gusl.common.pojo.entity.JudgeTaskMessage;

/**
 * Judge 测评服务接口。
 */
public interface JudgeService {


    void judge(JudgeTaskMessage message);


    /**
     * 判断测评任务对应的业务记录是否已经进入终态。
     *
     * @param taskType 任务类型
     * @param businessId 业务记录 id
     * @return 是否已经进入终态
     */
    boolean isBusinessTerminal(String taskType, Long businessId);


    /**
     * 在任务达到最大重试次数后写入系统错误终态。
     *
     * @param taskType 任务类型
     * @param businessId 业务记录 id
     * @param errorMessage 系统错误信息
     */
    void markSystemError(String taskType, Long businessId, String errorMessage);

}
