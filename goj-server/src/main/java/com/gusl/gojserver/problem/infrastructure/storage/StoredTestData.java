package com.gusl.gojserver.problem.infrastructure.storage;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 已保存的测试数据文件，返回路径、原包摘要和测试点数量。 */
@Data
@AllArgsConstructor
public class StoredTestData {

    private String archiveName;

    private String archivePath;

    private String storagePath;

    private String archiveSha256;

    private Integer testNodeCount;
}
