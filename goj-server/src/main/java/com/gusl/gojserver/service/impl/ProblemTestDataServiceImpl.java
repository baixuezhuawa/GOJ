package com.gusl.gojserver.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gusl.common.common.BaseException;
import com.gusl.common.constant.ProblemStatus;
import com.gusl.common.constant.ProblemTestDataStatus;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.common.pojo.entity.ProblemTestData;
import com.gusl.gojserver.config.properties.SysProperties;
import com.gusl.gojserver.mapper.ProblemMapper;
import com.gusl.gojserver.mapper.ProblemTestDataMapper;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.service.ProblemTestDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProblemTestDataServiceImpl extends ServiceImpl<ProblemTestDataMapper, ProblemTestData> implements ProblemTestDataService {

    private final ProblemMapper problemMapper;

    private final ProblemTestDataMapper problemTestDataMapper;

    private final SysProperties sysProperties;


    /** 上传测试数据 */
    @Override
    public void uploadTestData(Long problemId, MultipartFile file, LoginUser loginUser) {
        if (file == null || file.isEmpty()) {
            throw new BaseException("测试数据压缩包不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.endsWith(".zip")) {
            throw new BaseException("测试数据必须使用 ZIP 压缩包");
        }

        // 判断问题是否存在
        Problem problem = problemMapper.selectById(problemId);
        if(problem == null){
            throw new BaseException("问题不存在");
        }
        // 作者校验是服务层的不变量；即使当前由创建草稿流程调用，后续单独复用上传方法也不能绕过。
        if(!problem.getAuthorId().equals(loginUser.getUserId())){
            throw new BaseException("问题作者与上传者不匹配");
        }

        // 插入测试数据
        ProblemTestData testData = ProblemTestData.builder()
                .problemId(problemId)
                .archiveName(originalFilename)
                .status(ProblemTestDataStatus.UPLOADING)
                .active(false)
                .build();
        problemTestDataMapper.insert(testData);

        Long testDataId = testData.getId();
        String uuid = UUID.fastUUID().toString();

        // 上传测试数据的暂存区路径
        Path uploadDir = dataRoot().resolve("staging")
                .resolve("upload-" + uuid)
                .normalize();

        // 压缩包存放地点, 解压后存放目录
        Path archivePath = uploadDir.resolve("original.zip");
        Path extractedDir = uploadDir.resolve("extracted");

        try {
            // 为当前测试数创建暂存区
            Files.createDirectories(uploadDir);

            // 为该压缩包计算sha256值
            String sha256;
            try (InputStream inputStream = file.getInputStream()) {
                sha256 = saveAndCalculateSha256(inputStream, archivePath);
            }

            // 更新上传状态
            int uploadedRows = problemTestDataMapper.update(
                    Wrappers.<ProblemTestData>lambdaUpdate()
                            .set(ProblemTestData::getStatus, ProblemTestDataStatus.UPLOADED)
                            .eq(ProblemTestData::getId, testDataId)
                            .eq(ProblemTestData::getStatus, ProblemTestDataStatus.UPLOADING)
            );
            if (uploadedRows != 1) {
                throw new BaseException("测试数据上传状态更新失败");
            }

            // 安全解压
            unzipSafely(archivePath, extractedDir);

            // 检验解压后测试数据的合法性, 返回测试点数
            int testNodeCount = validateTestData(extractedDir);

            // 更新测试数据状态
            int extractedRows = problemTestDataMapper.update(
                    Wrappers.<ProblemTestData>lambdaUpdate()
                            .set(ProblemTestData::getArchiveSha256, sha256)
                            // 存储相对路径 /upload-{testDataId}/extracted
                            .set(ProblemTestData::getStoragePath, "staging/upload-" + uuid + "/extracted")
                            .set(ProblemTestData::getTestNodeCount, testNodeCount)
                            .set(ProblemTestData::getStatus, ProblemTestDataStatus.EXTRACTED)
                            .eq(ProblemTestData::getId, testDataId)
                            .eq(ProblemTestData::getStatus, ProblemTestDataStatus.UPLOADED)
            );
            if (extractedRows != 1) {
                throw new BaseException("测试数据校验状态更新失败");
            }
        } catch (Exception exception) {
            /*
             该条测试数据上传失败, 更新当前测试的状态为无效状态.
             后续靠 定时任务/管理员 清理这条信息(和对应可以不完整的数据).
             */
            problemTestDataMapper.update(
                    Wrappers.<ProblemTestData>lambdaUpdate()
                            .set(ProblemTestData::getStatus, ProblemTestDataStatus.INVALID)
                            .set(ProblemTestData::getRemark, exception.getMessage())
                            .eq(ProblemTestData::getId, testData.getId())
            );
            throw new BaseException("测试数据解压或结构检查失败：" + exception.getMessage());
        }
        log.info("上传成功 {}", uuid);
    }


    /** 直接更新测试数据 */
    @Override
    public void updateTestDataWithdraw(Long problemId, MultipartFile data, LoginUser loginUser) {
       /*
       供作者使用, 管理员的话, 不知道该怎么办好
       添加权限? 也就是对这个问题的编辑权 problem:problemId:manager?
       那就不需要检验作者是否匹配问题作者了, 只需要在Controller层判断是否有权限就行了.
       场景:
            草稿/驳回阶段, 作者需要修改测试数据
            发布/禁用阶段, 作者需要修改测试数据, 因发现数据弱, 数据错误等问题.
            比赛阶段, 零时修改测试数据.

        目前只支持修改未发布的题目数据
        如果有共同修改测试数据呢? 怎么判断原子性
        */

        int updateEffectLines = problemMapper.update(
               Wrappers.<Problem>lambdaUpdate()
                       .set(Problem::getStatus, ProblemStatus.DRAFT)
                       .eq(Problem::getId, problemId)
                       .eq(Problem::getAuthorId, loginUser.getUserId())
                       .in(Problem::getStatus, ProblemStatus.WITHDRAW, ProblemStatus.DRAFT)
        );

        if(updateEffectLines == 0){
            throw new BaseException("该问题不存在");
        }

        /*
        判断问题的状态, 如果是发布/禁用状态 dataRoot/testData/{path}
        其他则是 dataRoot/stating/{path}
         */

        // 未发布的问题, 一定只有一条测试数据信息的
        ProblemTestData testDataDb = problemTestDataMapper.selectOne(
                Wrappers.<ProblemTestData>lambdaQuery()
                        .eq(ProblemTestData::getProblemId, problemId)
                        .eq(ProblemTestData::getActive, 0)
                        .eq(ProblemTestData::getStatus, ProblemTestDataStatus.EXTRACTED)
        );
        if(testDataDb == null){
            throw new BaseException("该测试数据不存在");
        }

        String uuid = UUID.fastUUID().toString();

        // 上传测试数据的暂存区路径
        Path uploadDir = dataRoot().resolve("staging")
                .resolve("upload-" + uuid)
                .normalize();

        // 压缩包存放地点, 解压后存放目录
        Path archivePath = uploadDir.resolve("original.zip");
        Path extractedDir = uploadDir.resolve("extracted");

        try {
            // 为当前测试数创建暂存区
            Files.createDirectories(uploadDir);

            // 为该压缩包计算sha256值
            String sha256;
            try (InputStream inputStream = data.getInputStream()) {
                sha256 = saveAndCalculateSha256(inputStream, archivePath);
            }
            if(sha256.equals(testDataDb.getArchiveSha256())){
                throw new BaseException("上传重复文件");
            }

            // 安全解压
            unzipSafely(archivePath, extractedDir);

            // 检验路径是否正确, 如果不正确, 则进行删除
            int testNodeCount = validateTestData(extractedDir);

            updateEffectLines = problemTestDataMapper.update(
                    Wrappers.<ProblemTestData>lambdaUpdate()
                            .set(ProblemTestData::getTestNodeCount, testNodeCount)
                            .set(ProblemTestData::getArchiveSha256, sha256)
                            .set(ProblemTestData::getStoragePath, "staging/upload-" + uuid + "/extracted")
                            .set(ProblemTestData::getArchiveName, data.getOriginalFilename())
                            .eq(ProblemTestData::getId, testDataDb.getId())
                            .eq(ProblemTestData::getStatus, ProblemTestDataStatus.EXTRACTED)
                            .eq(ProblemTestData::getArchiveSha256, testDataDb.getArchiveSha256())
                            .eq(ProblemTestData::getStoragePath, testDataDb.getStoragePath())
                            // 似乎是通过更新时间就可以进行唯一性判断.
                            .eq(ProblemTestData::getUpdateTime, testDataDb.getUpdateTime())
            );

            // 或者通过更新时间判断, 是否原子, 用于解决并发修改下的问题.
            if(updateEffectLines == 0){
                throw new BaseException("该测试数据已被修改");
            }
        } catch (Exception e) {
            // 删除uploadDir
            FileUtil.del(uploadDir);
            throw new BaseException("更新测试数据失败: " + e.getMessage());
        }

        /*
        为了安全考虑不能就此
        更新成功, 后续设计定时任务清理旧数据, 这个旧数据的地址 testDataDb中存储有
        只需要加入某个待清理的表中就行了
         */


        log.info("更新成功 {}", uuid);
    }


    /**
     * 计算该题目的下一个正式测试数据版本号。
     */
    @Override
    public int nextVersion(Long problemId) {
        List<ProblemTestData> datasets = problemTestDataMapper.selectList(
                Wrappers.<ProblemTestData>lambdaQuery()
                        .eq(ProblemTestData::getProblemId, problemId)
                        .isNotNull(ProblemTestData::getVersion)
        );
        return datasets.stream()
                .map(ProblemTestData::getVersion)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }


    private Path dataRoot() {
        return Path.of(sysProperties.getDataRoot()).toAbsolutePath().normalize();
    }

    /**
     * 保存上传的压缩包，并计算其 SHA-256。
     * @param inputStream 上传文件输入流
     * @param targetPath 暂存目录中的压缩包路径
     * @return 由 64 个十六进制字符组成的 SHA-256
     */
    private String saveAndCalculateSha256(InputStream inputStream, Path targetPath) throws IOException {
        MessageDigest digest = createSha256Digest();

        try (
                InputStream digestInput = new DigestInputStream(inputStream, digest);
                OutputStream output = Files.newOutputStream(targetPath, StandardOpenOption.CREATE_NEW)
        ) {
            digestInput.transferTo(output);
        }

        return HexFormat.of().formatHex(digest.digest());
    }


    /**
     * @return SHA-256 摘要计算器。
     */
    private MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            // Java 标准环境必须支持 SHA-256，出现该异常说明运行环境异常。
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }


    /**
     * 安全解压测试数据
     * @param archivePath 需要解压文件地址
     * @param extractedDir 提取到的目标目录下
     */
    private void unzipSafely(Path archivePath, Path extractedDir) throws IOException {
        Files.createDirectories(extractedDir);

        long totalBytes = 0;
        int entryCount = 0;

        try (ZipInputStream zipInput = new ZipInputStream(Files.newInputStream(archivePath))) {
            ZipEntry entry;

            while ((entry = zipInput.getNextEntry()) != null) {
                if (++entryCount > sysProperties.getFile().getMaxEntry()) {
                    throw new BaseException("压缩包文件数量超过限制");
                }

                Path outputPath = extractedDir
                        .resolve(entry.getName())
                        .normalize();

                if (!outputPath.startsWith(extractedDir)) {
                    throw new BaseException("压缩包包含非法路径");
                }

                // 如果是文件夹就创建对应文件夹
                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                    continue;
                }

                // 对应文件, 防止上级目录没有创建, 因为遍历顺序未知.
                Files.createDirectories(outputPath.getParent());

                // 写入
                try (OutputStream output = Files.newOutputStream(
                        outputPath,
                        StandardOpenOption.CREATE_NEW
                    )
                ) {
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = zipInput.read(buffer)) != -1) {
                        totalBytes += length;
                        if (totalBytes > sysProperties.getFile().getMaxTotalBytes()) {
                            throw new BaseException("解压后文件总大小超过限制");
                        }
                        output.write(buffer, 0, length);
                    }
                }
            }
        }

        if (entryCount == 0) {
            throw new BaseException("压缩包不能为空");
        }
    }


    /**
     * 检查测试点是否从 test1 开始连续，并验证每个测试点文件完整。
     *
     * @param extractedDir 解压后的测试数据目录
     * @return 测试点数量
     */
    private int validateTestData(Path extractedDir) throws IOException {
        int testIndex = 1;

        while (Files.isDirectory(extractedDir.resolve("test" + testIndex))) {
            Path testDir = extractedDir.resolve("test" + testIndex);

            // 获取输入输出文件路径
            Path inputFilePath = testDir.resolve("input.txt");
            Path outPutFilePath = testDir.resolve("output.txt");

            requireRegularFile(inputFilePath);
            requireRegularFile(outPutFilePath);

            // 验证是否为文件, 读一下就知道了
            Files.readString(inputFilePath);
            Files.readString(outPutFilePath);

            testIndex++;
        }

        int testNodeCount = testIndex - 1;
        if (testNodeCount == 0) {
            throw new BaseException("至少需要一个测试点");
        }

        // 还需要扫描 extractedDir，拒绝 test3 存在但 test2 缺失等情况。
        validateNoUnexpectedEntries(extractedDir, testNodeCount);

        return testNodeCount;
    }


    /**
     * 检查测试数据路径是否为真实存在的普通文件。
     * <p>拒绝目录和符号链接，避免后续 Judge 读取到测试目录之外的内容。</p>
     */
    private void requireRegularFile(Path filePath) {
        if (!Files.exists(filePath, LinkOption.NOFOLLOW_LINKS)) {
            throw new BaseException(
                    "缺少测试数据文件：" + filePath.getFileName()
            );
        }

        if (!Files.isRegularFile(filePath, LinkOption.NOFOLLOW_LINKS)) {
            throw new BaseException(
                    "测试数据路径不是普通文件：" + filePath.getFileName()
            );
        }

        if (!Files.isReadable(filePath)) {
            throw new BaseException(
                    "测试数据文件不可读：" + filePath.getFileName()
            );
        }
    }


    /**
     * 检查解压目录中是否存在编号不连续的测试点或多余文件。
     *
     * <p>根目录只能包含连续的 {@code test1} 到 {@code testN} 目录；
     * 每个测试点目录只能包含 {@code input.txt} 和 {@code output.txt}。</p>
     *
     * @param extractedDir 解压后的测试数据根目录
     * @param testNodeCount 已确认连续存在的测试点数量
     */
    private void validateNoUnexpectedEntries(Path extractedDir, int testNodeCount) throws IOException {
        try (Stream<Path> entries = Files.list(extractedDir)) {
            for (Path entry : entries.toList()) {
                String entryName = entry.getFileName().toString();

                if (!Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    throw new BaseException(
                            "测试数据根目录不能包含文件：" + entryName
                    );
                }

                if (!entryName.matches("test[1-9]\\d*")) {
                    throw new BaseException(
                            "非法测试点目录：" + entryName
                    );
                }

                int testIndex = Integer.parseInt(entryName.substring(4));
                String expectedName = "test" + testIndex;

                if (testIndex > testNodeCount || !entryName.equals(expectedName)) {
                    throw new BaseException(
                            "测试点编号必须从 test1 开始连续：" + entryName
                    );
                }

                validateTestDirectory(entry);
            }
        }
    }


    /**
     * 检查单个测试点目录只能包含输入文件和标准输出文件。
     *
     * @param testDir 单个测试点目录
     */
    private void validateTestDirectory(Path testDir) throws IOException {
        Set<String> allowedNames = Set.of("input.txt", "output.txt");

        try (Stream<Path> entries = Files.list(testDir)) {
            for (Path entry : entries.toList()) {
                String fileName = entry.getFileName().toString();

                if (!allowedNames.contains(fileName)) {
                    throw new BaseException(testDir.getFileName() + " 包含不允许的文件：" + fileName);
                }
            }
        }
    }

}
