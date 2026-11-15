package com.gusl.gojserver.problem;

import com.gusl.common.common.BaseException;
import com.gusl.gojserver.problem.infrastructure.storage.StoredTestData;
import com.gusl.gojserver.problem.infrastructure.storage.TestDataStorage;
import com.gusl.gojserver.shared.infrastructure.config.properties.SysProperties;
import com.gusl.gojserver.shared.infrastructure.config.properties.entity.FileProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** 测试真实 ZIP 保存、校验、摘要和文件清理。 */
class TestDataStorageTests {

    @TempDir
    Path root;

    private TestDataStorage storage;

    @BeforeEach
    void setUp() {
        SysProperties properties = new SysProperties();
        properties.setDataRoot(root.toString());
        FileProperties file = new FileProperties();
        file.setMaxEntry(20);
        file.setMaxTotalBytes(1024L);
        properties.setFile(file);
        storage = new TestDataStorage(properties);
    }

    @Test
    void savesOriginalArchiveAndFullFileHash() throws Exception {
        byte[] zip = zip("test1/input.txt", "1 2", "test1/output.txt", "3");
        StoredTestData saved = storage.save(1L, 7, file(zip));

        assertArrayEquals(zip, Files.readAllBytes(storage.getArchive(saved.getArchivePath()).getFile().toPath()));
        // Java 17 的 HexFormat 用于独立核对完整文件摘要。
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(zip)), saved.getArchiveSha256());
        assertEquals(1, saved.getTestNodeCount());
        assertTrue(saved.getStoragePath().startsWith("testData/p1/v7-"));
        assertEquals("3", Files.readString(root.resolve(saved.getStoragePath()).resolve("test1/output.txt")));
        storage.requireReadable(saved.getStoragePath(), 1);
    }

    @Test
    void invalidReplacementDoesNotDestroyPreviousFiles() throws Exception {
        StoredTestData first = storage.save(1L, 1, file(zip("test1/input.txt", "1", "test1/output.txt", "2")));
        assertThrows(BaseException.class, () -> storage.save(1L, 1, file(zip("test1/input.txt", "broken"))));
        storage.requireReadable(first.getStoragePath(), 1);
        assertEquals(3, countFiles());
    }

    @Test
    void deletingLegacyDirectoryDoesNotDeleteReplacement() throws Exception {
        Path legacy = root.resolve("testData/p1/v1");
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("old.txt"), "old");
        StoredTestData replacement = storage.save(1L, 1, file(zip("test1/input.txt", "1", "test1/output.txt", "2")));

        storage.remove(null, legacy.toString());
        assertFalse(Files.exists(legacy));
        storage.requireReadable(replacement.getStoragePath(), 1);
        storage.remove(replacement.getArchivePath(), replacement.getStoragePath());
        assertEquals(0, countFiles());
    }

    @Test
    void rejectsBrokenZipTraversalAndNonContinuousCases() throws Exception {
        assertThrows(BaseException.class, () -> storage.save(1L, 1, file(new byte[]{1, 2, 3})));
        assertThrows(BaseException.class, () -> storage.save(1L, 1, file(zip("../escape.txt", "bad"))));
        assertThrows(BaseException.class, () -> storage.save(1L, 1, file(zip(
                "test1/input.txt", "1", "test1/output.txt", "2", "test3/input.txt", "1", "test3/output.txt", "2"))));
        assertThrows(BaseException.class, () -> storage.save(1L, 1, file(zip(
                "test1/input.txt", "a".repeat(1025), "test1/output.txt", "2"))));
        assertEquals(0, countFiles());
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "测试数据.zip", "application/zip", bytes);
    }

    private long countFiles() throws Exception {
        try (java.util.stream.Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private byte[] zip(String... entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (int i = 0; i < entries.length; i += 2) {
                zip.putNextEntry(new ZipEntry(entries[i]));
                zip.write(entries[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
