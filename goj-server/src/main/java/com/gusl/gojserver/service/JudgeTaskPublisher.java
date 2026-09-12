package com.gusl.gojserver.service;

import com.gusl.common.pojo.entity.JudgeTaskMessage;

/**
 * 消息发布抽象接口
 */
public interface JudgeTaskPublisher {

    void publish(JudgeTaskMessage message);

}
