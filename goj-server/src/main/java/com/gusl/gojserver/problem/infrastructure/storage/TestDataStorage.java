package com.gusl.gojserver.problem.infrastructure.storage;

import cn.hutool.core.io.FileUtil;
import com.gusl.common.common.BaseException;
import com.gusl.common.utils.Sha256Utils;
import com.gusl.gojserver.shared.infrastructure.config.properties.SysProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** 测试数据文件存储，集中处理原始 ZIP、解压、结构校验和摘要。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestDataStorage {

    private final SysProperties sysProperties;

    /** 保存原始 ZIP 和解压内容，所有文件直接位于正式目录。 */
    public StoredTestData save(Long problemId, Integer version, MultipartFile file) {
        validateArchive(file);
        // 每次保存独立文件目录，校验成功后才切换数据库路径，不破坏旧版本文件。
        Path directory = root().resolve("testData/p" + problemId)
                .resolve("v" + version + "-" + UUID.randomUUID());
        Path archive = directory.resolve("data.zip");
        Path cases = directory.resolve("cases");
        try {
            Files.createDirectories(directory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, archive);
            }
            String sha256 = calculateSha256(archive);
            extract(archive, cases);
            int count = validateTestData(cases);
            return new StoredTestData(file.getOriginalFilename(), relative(archive), relative(cases), sha256, count);
        } catch (Exception exception) {
            removeDirectory(directory);
            throw new BaseException("测试数据保存失败：" + exception.getMessage());
        }
    }

    /** 校验上传文件类型；ZIP 内容由解压方法实际解析。 */
    private void validateArchive(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BaseException("测试数据压缩包不能为空");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new BaseException("测试数据必须使用 ZIP 压缩包");
        }
    }

    /** 计算完整原始文件的 SHA-256，不依赖 ZIP 解压流的读取范围。 */
    private String calculateSha256(Path archive) throws IOException {
        MessageDigest digest = Sha256Utils.createSha256Digest();
        try (InputStream input = new DigestInputStream(Files.newInputStream(archive), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        // Java 17 的 HexFormat 将摘要字节转换为十六进制字符串。
        return HexFormat.of().formatHex(digest.digest());
    }

    /** 解压 ZIP，限制文件数量和解压大小，并阻止目录越界。 */
    private void extract(Path archive, Path cases) throws IOException {
        Files.createDirectories(cases);
        long totalBytes = 0;
        int entryCount = 0;
        // ZipFile 读取完整 ZIP 目录，损坏或不是 ZIP 的文件会直接失败。
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++entryCount > sysProperties.getFile().getMaxEntry()) {
                    throw new BaseException("压缩包文件数量超过限制");
                }
                Path target = cases.resolve(entry.getName()).normalize();
                if (!target.startsWith(cases)) {
                    throw new BaseException("压缩包包含非法路径");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                try (InputStream input = zip.getInputStream(entry);
                     OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = input.read(buffer)) != -1) {
                        totalBytes += length;
                        if (totalBytes > sysProperties.getFile().getMaxTotalBytes()) {
                            throw new BaseException("解压后文件总大小超过限制");
                        }
                        output.write(buffer, 0, length);
                    }
                }
            }
        }
    }

    /** 发布或启用时确认当前测试点目录与记录一致。 */
    public void requireReadable(String storagePath, Integer expectedCount) {
        try {
            int count = validateTestData(resolve(storagePath));
            if (!Integer.valueOf(count).equals(expectedCount)) {
                throw new BaseException("测试点数量与记录不一致");
            }
        } catch (IOException exception) {
            throw new BaseException("测试数据不可读：" + exception.getMessage());
        }
    }

    /** 读取保留的原始 ZIP，不重新打包解压目录。 */
    public Resource getArchive(String archivePath) {
        if (archivePath == null) {
            throw new BaseException("该历史数据没有保留原始 ZIP，请重新上传");
        }
        Path archive = resolve(archivePath);
        requireRegularFile(archive);
        return new FileSystemResource(archive);
    }

    /** 删除这次上传的完整目录；旧数据只删除其明确记录的路径。 */
    public void remove(String archivePath, String storagePath) {
        if (storagePath == null) {
            return;
        }
        try {
            Path cases = resolve(storagePath);
            if (archivePath != null) {
                Path archive = resolve(archivePath);
                if (cases.getFileName().toString().equals("cases")
                        && archive.getFileName().toString().equals("data.zip")
                        && archive.getParent().equals(cases.getParent())) {
                    removeDirectory(cases.getParent());
                    return;
                }
                removeDirectory(archive);
            }
            removeDirectory(cases);
        } catch (RuntimeException exception) {
            log.warn("测试数据文件清理失败，请人工清理：{}", storagePath, exception);
        }
    }

    /** 解析数据路径，兼容旧记录的绝对路径，但限定在数据根目录内。 */
    private Path resolve(String path) {
        if (path == null) {
            throw new BaseException("测试数据路径为空");
        }
        Path resolved = root().resolve(path).normalize();
        if (resolved.equals(root()) || !resolved.startsWith(root())) {
            throw new BaseException("测试数据路径不在数据根目录内");
        }
        return resolved;
    }

    /** 获取文件根目录。 */
    private Path root() {
        return Path.of(sysProperties.getDataRoot()).toAbsolutePath().normalize();
    }

    /** 数据库存储相对路径，避免绑定服务器盘符。 */
    private String relative(Path path) {
        return root().relativize(path).toString().replace('\\', '/');
    }

    /** 尽力清理文件，清理失败不掩盖原始异常或已提交的数据库结果。 */
    private void removeDirectory(Path directory) {
        try {
            resolve(directory.toString());
            if (!FileUtil.del(directory)) {
                log.warn("测试数据文件清理失败，请人工清理：{}", directory);
            }
        } catch (RuntimeException exception) {
            log.warn("测试数据文件清理失败，请人工清理：{}", directory, exception);
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

            // 当前判题器按 UTF-8 文本读取输入输出，上传时验证编码。
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
            throw new BaseException("缺少测试数据文件：" + filePath.getFileName());
        }

        if (!Files.isRegularFile(filePath, LinkOption.NOFOLLOW_LINKS)) {
            throw new BaseException("测试数据路径不是普通文件：" + filePath.getFileName());
        }

        if (!Files.isReadable(filePath)) {
            throw new BaseException("测试数据文件不可读：" + filePath.getFileName());
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
            // Java 16 的 Stream.toList() 将目录流收集为列表，便于逐项校验。
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
            // Java 16 的 Stream.toList() 将目录流收集为列表，便于逐项校验。
            for (Path entry : entries.toList()) {
                String fileName = entry.getFileName().toString();

                if (!allowedNames.contains(fileName)) {
                    throw new BaseException(testDir.getFileName() + " 包含不允许的文件：" + fileName);
                }
            }
        }
    }

}
