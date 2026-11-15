package com.gusl.gojserver.problem.application;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.common.BaseException;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.common.pojo.entity.ProblemTestData;
import com.gusl.gojserver.problem.infrastructure.persistence.ProblemMapper;
import com.gusl.gojserver.problem.infrastructure.persistence.ProblemTestDataMapper;
import com.gusl.gojserver.problem.infrastructure.storage.StoredTestData;
import com.gusl.gojserver.problem.infrastructure.storage.TestDataStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.atomic.AtomicReference;

/** 测试数据服务实现，管理版本元信息，文件处理交给存储类。 */
@Service
@RequiredArgsConstructor
public class ProblemTestDataServiceImpl implements ProblemTestDataService {

    private final ProblemMapper problemMapper;
    private final ProblemTestDataMapper problemTestDataMapper;
    private final TestDataStorage storage;
    private final TransactionTemplate transactionTemplate;

    /** 保存管理员指定的版本，已有版本允许直接覆盖。 */
    @Override
    public Long saveTestData(Long problemId, Integer version, MultipartFile file, String remark) {
        if (version == null || version <= 0) {
            throw new BaseException("测试数据版本必须是正整数");
        }
        // 文件先在正式目录完成校验，不向数据库暴露未完成的数据。
        StoredTestData stored = storage.save(problemId, version, file);
        AtomicReference<ProblemTestData> oldRef = new AtomicReference<>();
        Long id;
        try {
            id = transactionTemplate.execute(status -> {
                lockProblem(problemId);
                ProblemTestData old = findVersion(problemId, version);
                oldRef.set(old);
                ProblemTestData current = BeanUtil.copyProperties(stored, ProblemTestData.class);
                current.setProblemId(problemId);
                current.setVersion(version);
                current.setRemark(remark);
                if (old == null) {
                    current.setActive(false);
                    problemTestDataMapper.insert(current);
                } else {
                    // 保留记录 id，比赛和验题的历史引用会读取替换后的内容。
                    current.setId(old.getId());
                    current.setActive(old.getActive());
                    problemTestDataMapper.updateById(current);
                }
                return current.getId();
            });
        } catch (RuntimeException exception) {
            storage.remove(stored.getArchivePath(), stored.getStoragePath());
            throw exception;
        }
        // 数据库提交成功后再删除旧文件；管理员自行安排覆盖操作时机。
        ProblemTestData old = oldRef.get();
        if (old != null) {
            storage.remove(old.getArchivePath(), old.getStoragePath());
        }
        return id;
    }

    /** 设置启用标记，同题最多启用一个版本。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setTestDataActive(Long problemId, Integer version, boolean active) {
        lockProblem(problemId);
        ProblemTestData data = requireVersion(problemId, version);
        if (active) {
            storage.requireReadable(data.getStoragePath(), data.getTestNodeCount());
            problemTestDataMapper.update(Wrappers.<ProblemTestData>lambdaUpdate()
                    .set(ProblemTestData::getActive, false).eq(ProblemTestData::getProblemId, problemId));
        }
        problemTestDataMapper.update(Wrappers.<ProblemTestData>lambdaUpdate()
                .set(ProblemTestData::getActive, active).eq(ProblemTestData::getId, data.getId()));
    }

    /** 删除任意版本，由管理员自行处理失效的比赛和验题引用。 */
    @Override
    public void deleteTestData(Long problemId, Integer version) {
        ProblemTestData data = transactionTemplate.execute(status -> {
            lockProblem(problemId);
            ProblemTestData current = requireVersion(problemId, version);
            problemTestDataMapper.deleteById(current.getId());
            return current;
        });
        storage.remove(data.getArchivePath(), data.getStoragePath());
    }

    /** 下载原始压缩包。 */
    @Override
    public Resource getArchive(Long problemId, Integer version) {
        return storage.getArchive(requireVersion(problemId, version).getArchivePath());
    }

    /** 发布题目时检查当前启用版本。 */
    @Override
    public ProblemTestData requireActiveTestData(Long problemId) {
        ProblemTestData data = problemTestDataMapper.selectOne(Wrappers.<ProblemTestData>lambdaQuery()
                .eq(ProblemTestData::getProblemId, problemId).eq(ProblemTestData::getActive, true));
        if (data == null) {
            throw new BaseException("题目没有已启用的测试数据");
        }
        storage.requireReadable(data.getStoragePath(), data.getTestNodeCount());
        return data;
    }

    /** 串行处理同题写操作，避免同时启用多个版本或覆盖时丢失更新。 */
    private void lockProblem(Long problemId) {
        Problem problem = problemMapper.selectOne(Wrappers.<Problem>lambdaQuery()
                .eq(Problem::getId, problemId).last("FOR UPDATE"));
        if (problem == null) {
            throw new BaseException("题目不存在");
        }
    }

    /** 查询指定版本。 */
    private ProblemTestData findVersion(Long problemId, Integer version) {
        return problemTestDataMapper.selectOne(Wrappers.<ProblemTestData>lambdaQuery()
                .eq(ProblemTestData::getProblemId, problemId).eq(ProblemTestData::getVersion, version));
    }

    /** 获取存在的版本。 */
    private ProblemTestData requireVersion(Long problemId, Integer version) {
        ProblemTestData data = findVersion(problemId, version);
        if (data == null) {
            throw new BaseException("测试数据版本不存在");
        }
        return data;
    }
}
