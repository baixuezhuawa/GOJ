package com.gusl.gojjudge.pojo.entity;

import com.gusl.common.constant.StorageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TestDataRef {

    /**
     * 测试数据集记录 id。
     */
    private Long testDataId;

    /**
     * 所属题目 id，主要用于一致性校验和日志。
     */
    private Long problemId;

    /**
     * 测试数据版本。
     */
    private Integer version;

    /**
     * 测试点数量。
     */
    private Integer testCaseCount;

    /**
     * 存储类型，例如 LOCAL_FILE、OBJECT_STORAGE。
     */
    private StorageType storageType;

    /**
     * 当前存储介质中的路径或对象 key。
     */
    private String storagePath;

}