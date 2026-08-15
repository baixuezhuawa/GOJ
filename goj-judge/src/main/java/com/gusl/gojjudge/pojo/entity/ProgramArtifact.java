package com.gusl.gojjudge.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 编译产物在 Judge 与沙箱之间的引用信息。
 *
 * <p>Judge 不保存或执行用户编译产物，只保存沙箱返回的 fileId，并在每个运行请求中
 * 通过该 ID 让沙箱复制文件到运行目录。</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProgramArtifact {

    /** 运行请求中使用的文件名，例如 {@code Main.jar}。 */
    private String activeFileName;

    /** go-judge {@code copyOutCached} 返回的缓存文件 ID。 */
    private String fileId;

    /** 直接复制进沙箱的源码；缓存文件模式下为空。 */
    private String content;

    /**
     * 创建缓存编译产物。
     *
     * @param activeFileName 沙箱内文件名
     * @param fileId go-judge 缓存文件 ID
     * @return 缓存文件产物
     */
    public static ProgramArtifact cached(String activeFileName, String fileId) {
        return new ProgramArtifact(activeFileName, fileId, null);
    }

    /**
     * 创建内联脚本产物。
     *
     * @param activeFileName 沙箱内脚本文件名
     * @param content 用户源码
     * @return 内联源码产物
     */
    public static ProgramArtifact inline(String activeFileName, String content) {
        return new ProgramArtifact(activeFileName, null, content);
    }
}
