package com.gusl.gojserver.submission.infrastructure.canal;

import com.gusl.gojserver.shared.infrastructure.config.properties.SubmissionCacheProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 提交同步健康状态，故障和积压时使查询绕过缓存。 */
@Component
@RequiredArgsConstructor
public class SubmissionSyncHealth {
    private final SubmissionCacheProperties properties;
    private volatile long lastSuccess;
    private volatile boolean caughtUp;

    /** 记录成功拉取和处理，空批次表示当前没有可消费的积压。 */
    public void success(long oldestEventTime) {
        lastSuccess = System.currentTimeMillis();
        caughtUp = oldestEventTime == 0 || lastSuccess - oldestEventTime <= properties.getMaxLagSeconds() * 1000;
    }

    /** 连接或处理失败后立即停用缓存读取。 */
    public void failed() {
        caughtUp = false;
    }

    /** 仅表示客户端消费健康；上游静默故障由快照核验期限兜底。 */
    public boolean usable() {
        return properties.isEnabled() && caughtUp && System.currentTimeMillis() - lastSuccess < 10000;
    }
}
