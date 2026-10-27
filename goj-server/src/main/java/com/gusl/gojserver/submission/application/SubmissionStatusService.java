package com.gusl.gojserver.submission.application;

import cn.hutool.core.bean.BeanUtil;
import com.gusl.common.common.BaseException;
import com.gusl.common.constant.SubmissionType;
import com.gusl.common.pojo.entity.Submission;
import com.gusl.gojserver.shared.infrastructure.config.properties.SubmissionCacheProperties;
import com.gusl.gojserver.submission.application.model.SubmissionStatusSnapshot;
import com.gusl.gojserver.submission.application.vo.SubmissionStatusVo;
import com.gusl.gojserver.submission.infrastructure.cache.SubmissionStatusCache;
import com.gusl.gojserver.submission.infrastructure.canal.SubmissionSyncHealth;
import com.gusl.gojserver.submission.infrastructure.persistence.SubmissionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Objects;

/** 提交状态服务，串行协调单实例中的 CDC 刷新和查询回填。 */
@Slf4j
@Service
public class SubmissionStatusService {
    private final SubmissionMapper submissionMapper;

    private final SubmissionStatusCache cache;

    private final SubmissionCacheProperties properties;

    private final SubmissionSyncHealth health;

    private final TransactionTemplate outsideTransaction;

    // 固定数量的分段锁避免按提交 ID 不断创建锁对象；冲突仅增加短暂等待。
    private final Object[] locks = new Object[256];

    /** 初始化主库读取边界及分段锁。 */
    public SubmissionStatusService(
            SubmissionMapper mapper,
            SubmissionStatusCache cache,
            SubmissionCacheProperties properties,
            SubmissionSyncHealth health,
            PlatformTransactionManager transactionManager
    ) {
        this.submissionMapper = mapper;
        this.cache = cache;
        this.properties = properties;
        this.health = health;
        outsideTransaction = new TransactionTemplate(transactionManager);
        outsideTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        for (int i = 0; i < locks.length; i++) locks[i] = new Object();
    }

    /** 查询并核对入口归属；完整详情可强制从主库刷新。 */
    public SubmissionStatusVo getStatus(Long id, String type, Long contestId, boolean refresh) {
        SubmissionStatusSnapshot snapshot;
        synchronized (locks[Math.floorMod(id.hashCode(), locks.length)]) {
            snapshot = null;
            if (!refresh && health.usable()) {
                try {
                    snapshot = cache.get(id);
                } catch (RuntimeException exception) {
                    log.warn("读取提交缓存失败，回源查询，submissionId={}", id);
                }
                if (snapshot != null && System.currentTimeMillis() - snapshot.getVerifiedAt()
                        >= properties.getVerifyAfterSeconds() * 1000) snapshot = null;
            }
            if (snapshot == null) snapshot = refreshLocked(id, false);
        }
        if (snapshot == null || !type.equals(snapshot.getSubmissionType())
                || !Objects.equals(contestId, snapshot.getContestId())) {
            throw new BaseException("提交不存在");
        }
        return BeanUtil.copyProperties(snapshot, SubmissionStatusVo.class);
    }

    /** CDC 必须成功写入或删除缓存后，才能确认消费。 */
    public void synchronize(Long id) {
        synchronized (locks[Math.floorMod(id.hashCode(), locks.length)]) {
            refreshLocked(id, true);
        }
    }

    /** 在锁内读取当前主库结果，再完整替换缓存，避免旧快照回填覆盖。 */
    private SubmissionStatusSnapshot refreshLocked(Long id, boolean requireCache) {
        long verifiedAt = System.currentTimeMillis();
        Submission submission = outsideTransaction.execute(status -> submissionMapper.findStatus(id));
        SubmissionStatusSnapshot snapshot = null;
        if (submission != null && (SubmissionType.REGULAR.equals(submission.getSubmissionType())
                || SubmissionType.CONTEST.equals(submission.getSubmissionType()))) {
            snapshot = BeanUtil.copyProperties(submission, SubmissionStatusSnapshot.class);
            snapshot.setVerifiedAt(verifiedAt);
        }
        if (properties.isEnabled()) {
            try {
                if (snapshot == null) cache.delete(id);
                else cache.put(snapshot, cache.get(id));
            } catch (RuntimeException exception) {
                if (requireCache) throw exception;
                log.warn("回填提交缓存失败，返回主库结果，submissionId={}", id);
            }
        }
        return snapshot;
    }
}
