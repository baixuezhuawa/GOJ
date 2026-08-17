package com.gusl.gojserver.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gusl.common.common.BaseException;
import com.gusl.common.common.PageQuery;
import com.gusl.common.common.PageResult;
import com.gusl.common.constant.ProblemStatus;
import com.gusl.common.constant.ProblemTestDataStatus;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.common.pojo.entity.ProblemTestData;
import com.gusl.gojserver.config.properties.SysProperties;
import com.gusl.gojserver.mapper.ProblemMapper;
import com.gusl.gojserver.mapper.ProblemTestDataMapper;
import com.gusl.gojserver.mapper.TagMapper;
import com.gusl.gojserver.mapper.UserMapper;
import com.gusl.gojserver.pojo.entity.User;
import com.gusl.gojserver.pojo.vo.AdminProblemReviewDetailVo;
import com.gusl.gojserver.pojo.vo.AdminProblemReviewListVo;
import com.gusl.gojserver.pojo.vo.ProblemTestDataReviewVo;
import com.gusl.gojserver.service.ProblemReviewService;
import com.gusl.gojserver.service.ProblemTestDataService;
import com.gusl.gojserver.service.support.PageFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 管理员题目审核服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemReviewServiceImpl implements ProblemReviewService {

    private final ProblemMapper problemMapper;
    private final ProblemTestDataMapper problemTestDataMapper;
    private final UserMapper userMapper;
    private final TagMapper tagMapper;
    private final SysProperties sysProperties;
    private final TransactionTemplate transactionTemplate;
    private final ProblemTestDataService problemTestDataService;
    private final PageFactory pageFactory;

    /**
     * 分页获取待审核题目。
     */
    @Override
    public PageResult<AdminProblemReviewListVo> getPendingReviews(PageQuery query) {
        Page<AdminProblemReviewListVo> page = pageFactory.create(query);
        return PageResult.of(problemMapper.selectPendingReviews(page, ProblemStatus.PENDING));
    }

    /**
     * 获取待审核题目详情和测试数据摘要。
     */
    @Override
    public AdminProblemReviewDetailVo getPendingReviewDetail(Long problemId) {
        Problem problem = requirePendingProblem(problemId, false);
        ProblemTestData testData = requirePendingTestData(problemId, false);

        AdminProblemReviewDetailVo detail = BeanUtil.copyProperties(problem, AdminProblemReviewDetailVo.class);
        detail.setTags(tagMapper.getTagByProblemId(problemId));
        detail.setTestData(BeanUtil.copyProperties(testData, ProblemTestDataReviewVo.class));

        User author = userMapper.selectById(problem.getAuthorId());
        if (author != null) {
            detail.setUsername(author.getUsername());
        }
        return detail;
    }

    /**
     * 将暂存测试数据移动到正式目录，并在同一个数据库事务内发布题目和激活数据版本。
     */
    @Override
    public void approve(Long problemId) {
        AtomicReference<Path> sourceRef = new AtomicReference<>();
        AtomicReference<Path> destinationRef = new AtomicReference<>();
        AtomicBoolean moved = new AtomicBoolean(false);

        try {
            transactionTemplate.executeWithoutResult(status -> {
                Problem problem = requirePendingProblem(problemId, true);
                ProblemTestData testData = requirePendingTestData(problemId, true);
                int version = problemTestDataService.nextVersion(problemId);

                Path source = Path.of(sysProperties.getDataRoot(), testData.getStoragePath());

                Path destination = resolveOfficialDataRoot()
                        .resolve("p" + problemId)
                        .resolve("v" + version);

                sourceRef.set(source);
                destinationRef.set(destination);
                moveDirectory(source, destination);
                moved.set(true);

                // 同一题目只能存在一个 active 数据版本，旧版本保留元信息用于追踪。
                problemTestDataMapper.update(
                        Wrappers.<ProblemTestData>lambdaUpdate()
                                .set(ProblemTestData::getActive, false)
                                .set(ProblemTestData::getStatus, ProblemTestDataStatus.RETIRED)
                                .eq(ProblemTestData::getProblemId, problemId)
                                .eq(ProblemTestData::getActive, true)
                );

                int testDataRows = problemTestDataMapper.update(
                        Wrappers.<ProblemTestData>lambdaUpdate()
                                .set(ProblemTestData::getVersion, version)
                                .set(ProblemTestData::getStoragePath, "testData/p" + problemId + "/v" + version)
                                .set(ProblemTestData::getStatus, ProblemTestDataStatus.READY)
                                .set(ProblemTestData::getActive, true)
                                .set(ProblemTestData::getRemark, null)
                                .eq(ProblemTestData::getId, testData.getId())
                                .eq(ProblemTestData::getProblemId, problemId)
                                .eq(ProblemTestData::getActive, false)
                                .eq(ProblemTestData::getStatus, ProblemTestDataStatus.EXTRACTED)
                );
                if (testDataRows != 1) {
                    throw new BaseException("测试数据状态已变化，请刷新后重试");
                }

                int problemRows = problemMapper.update(
                        Wrappers.<Problem>lambdaUpdate()
                                .set(Problem::getStatus, ProblemStatus.PUBLISH)
                                .set(Problem::getRemark, null)
                                .eq(Problem::getId, problem.getId())
                                .eq(Problem::getStatus, ProblemStatus.PENDING)
                );
                if (problemRows != 1) {
                    throw new BaseException("题目审核状态已变化，请刷新后重试");
                }
            });
        } catch (RuntimeException exception) {
            if (moved.get()) {
                restoreStagingDirectory(destinationRef.get(), sourceRef.get());
            }
            throw exception;
        }

        log.info("题目 {} 审核通过，测试数据已发布", problemId);
    }

    /**
     * 驳回题目并保存审核意见。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long problemId, String remark) {
        if (remark == null || remark.isBlank()) {
            throw new BaseException("驳回原因不能为空");
        }
        String normalizedRemark = remark.trim();
        if (normalizedRemark.length() > 500) {
            throw new BaseException("驳回原因不能超过 500 个字符");
        }

        int affectedRows = problemMapper.update(
                Wrappers.<Problem>lambdaUpdate()
                        .set(Problem::getStatus, ProblemStatus.WITHDRAW)
                        .set(Problem::getRemark, normalizedRemark)
                        .eq(Problem::getId, problemId)
                        .eq(Problem::getStatus, ProblemStatus.PENDING)
        );
        if (affectedRows != 1) {
            throw new BaseException("题目不存在或已被其他管理员审核");
        }
        log.info("题目 {} 审核未通过", problemId);
    }


    /**
     * 查询待审核题目；审核写操作会增加行锁，避免两个管理员同时处理同一题目。
     */
    private Problem requirePendingProblem(Long problemId, boolean forUpdate) {
        if (problemId == null) {
            throw new BaseException("题目 id 不能为空");
        }
        var query = Wrappers.<Problem>lambdaQuery()
                .eq(Problem::getId, problemId)
                .eq(Problem::getStatus, ProblemStatus.PENDING);
        if (forUpdate) {
            query.last("FOR UPDATE");
        }

        Problem problem = problemMapper.selectOne(query);
        if (problem == null) {
            throw new BaseException("待审核题目不存在或已被处理");
        }
        return problem;
    }


    /**
     * 获取最近一次已完成解压校验、但尚未激活的测试数据。
     */
    private ProblemTestData requirePendingTestData(Long problemId, boolean forUpdate) {
        var query = Wrappers.<ProblemTestData>lambdaQuery()
                .eq(ProblemTestData::getProblemId, problemId)
                .eq(ProblemTestData::getActive, false)
                .eq(ProblemTestData::getStatus, ProblemTestDataStatus.EXTRACTED)
                .orderByDesc(ProblemTestData::getId)
                .last(forUpdate ? "LIMIT 1 FOR UPDATE" : "LIMIT 1");

        ProblemTestData testData = problemTestDataMapper.selectOne(query);
        if (testData == null) {
            throw new BaseException("题目没有可供审核的测试数据");
        }
        return testData;
    }



    /**
     * 将目录移动到正式测试数据区；文件系统不支持原子移动时退化为普通移动。
     */
    private void moveDirectory(Path source, Path destination) {
        try {
            Files.createDirectories(destination.getParent());
            try {
                Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(source, destination);
            }
        } catch (IOException exception) {
            log.warn("文件从 {} 移动到 {} 失败", source, destination, exception);
            throw new BaseException("测试数据移动到正式目录失败");
        }
    }



    /**
     * 数据库事务失败时尽量将已移动的目录放回暂存区。
     */
    private void restoreStagingDirectory(Path destination, Path source) {
        if (destination == null || source == null || !Files.exists(destination)) {
            return;
        }
        try {
            Files.createDirectories(source.getParent());
            Files.move(destination, source);
        } catch (IOException restoreException) {
            log.error("数据库更新失败后无法恢复测试数据目录，请人工处理：{} -> {}", destination, source, restoreException);
        }
    }



    private Path resolveDataRoot() {
        return Path.of(sysProperties.getDataRoot()).toAbsolutePath().normalize();
    }



    private Path resolveOfficialDataRoot() {
        return resolveDataRoot().resolve("testData").normalize();
    }
}
