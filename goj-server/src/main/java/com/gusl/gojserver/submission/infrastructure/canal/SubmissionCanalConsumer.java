package com.gusl.gojserver.submission.infrastructure.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.gusl.gojserver.shared.infrastructure.config.properties.SubmissionCacheProperties;
import com.gusl.gojserver.submission.application.SubmissionStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import java.net.InetSocketAddress;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 提交变更消费者，管理单实例 Canal 连接、批次确认及退出。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubmissionCanalConsumer implements SmartLifecycle {
    private final SubmissionCacheProperties properties;
    private final SubmissionStatusService statusService;
    private final SubmissionSyncHealth health;
    private volatile boolean running;
    private volatile CanalConnector connector;
    private ExecutorService executor;

    /** 应用就绪后启动一个后台线程，不阻塞 Spring 启动。 */
    @Override
    public synchronized void start() {
        if (running || !properties.isEnabled()) return;
        if (properties.getActiveTtlSeconds() <= 0 || properties.getTerminalTtlSeconds() <= 0
                || properties.getTerminalJitterSeconds() < 0 || properties.getTerminalJitterSeconds() > 86400
                || properties.getVerifyAfterSeconds() <= 0 || properties.getRetryMillis() <= 0
                || properties.getBatchSize() <= 0 || properties.getMaxLagSeconds() <= 0
                || !properties.getDatabase().matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("提交缓存的有效期、批量大小及重试配置不合法");
        }
        running = true;
        executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "submission-canal");
            thread.setDaemon(true);
            return thread;
        });
        executor.submit(this::consume);
    }

    /** 断线或写入失败后重连，从未确认位置重试。 */
    private void consume() {
        while (running && !Thread.currentThread().isInterrupted()) {
            CanalConnector current = CanalConnectors.newSingleConnector(
                    new InetSocketAddress(properties.getHost(), properties.getPort()),
                    properties.getDestination(), properties.getUsername(), properties.getPassword());
            connector = current;
            try {
                current.connect();
                current.subscribe(properties.getDatabase() + "\\.submission");
                current.rollback();
                while (running) {
                    Message message = current.getWithoutAck(properties.getBatchSize(), 1L, TimeUnit.SECONDS);
                    long batchId = message.getId();
                    if (batchId == -1) {
                        health.success(0);
                        pause(200);
                        continue;
                    }
                    try {
                        long oldest = process(message);
                        current.ack(batchId);
                        health.success(oldest);
                    } catch (Exception exception) {
                        health.failed();
                        current.rollback(batchId);
                        throw exception;
                    }
                }
            } catch (Exception exception) {
                health.failed();
                if (running) log.warn("提交状态同步失败，将重试：{}", exception.getMessage());
            } finally {
                try {
                    current.disconnect();
                } catch (RuntimeException exception) {
                    log.debug("Canal 连接已断开");
                }
            }
            if (running) pause(properties.getRetryMillis());
        }
    }

    /** 解析提交 ID，同一批次只读取一次当前主库摘要，不重放历史行镜像。 */
    public long process(Message message) throws Exception {
        Set<Long> ids = new LinkedHashSet<>();
        long oldest = 0;
        for (CanalEntry.Entry entry : message.getEntries()) {
            if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA
                    || !properties.getDatabase().equals(entry.getHeader().getSchemaName())
                    || !"submission".equals(entry.getHeader().getTableName())) continue;
            long timestamp = entry.getHeader().getExecuteTime();
            if (timestamp > 0) oldest = oldest == 0 ? timestamp : Math.min(oldest, timestamp);
            CanalEntry.RowChange change = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            if (change.getIsDdl()) {
                // 首次增量定位可能包含建表事件；建表本身没有待清理的结果行。
                if (change.getEventType() == CanalEntry.EventType.CREATE) continue;
                // 无逐行 ID 的结构变更不能静默跳过，保留批次等待人工检查及缓存清理。
                throw new IllegalStateException("提交表结构发生变化，请检查兼容性并清理结果缓存后恢复消费");
            }
            CanalEntry.EventType type = change.getEventType();
            if (type != CanalEntry.EventType.INSERT && type != CanalEntry.EventType.UPDATE
                    && type != CanalEntry.EventType.DELETE) continue;
            for (CanalEntry.RowData row : change.getRowDatasList()) {
                boolean found = false;
                for (CanalEntry.Column column : type == CanalEntry.EventType.DELETE
                        ? row.getBeforeColumnsList() : row.getAfterColumnsList()) {
                    if ("id".equals(column.getName()) && !column.getIsNull()) {
                        ids.add(Long.valueOf(column.getValue()));
                        found = true;
                        break;
                    }
                }
                if (!found) throw new IllegalStateException("提交变更缺少主键，无法同步");
            }
        }
        for (Long id : ids) statusService.synchronize(id);
        return oldest;
    }

    /** 可中断等待，避免空批次及故障导致高速重试。 */
    private void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    /** 关闭连接解除拉取等待，并回收后台线程。 */
    @Override
    public synchronized void stop() {
        running = false;
        health.failed();
        if (executor != null) executor.shutdownNow();
        CanalConnector current = connector;
        if (current != null) {
            try {
                current.disconnect();
            } catch (RuntimeException exception) {
                log.debug("Canal 连接关闭时已断开");
            }
        }
        if (executor != null) {
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("旧 Canal 消费线程尚未退出，不能启动第二个消费者");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待 Canal 消费线程退出被中断", exception);
            }
        }
    }

    /** 返回后台消费状态。 */
    @Override
    public boolean isRunning() {
        return running;
    }
}
