package com.gusl.gojserver.problem.application;

import com.gusl.common.pojo.entity.ProblemTestData;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/** 测试数据服务，管理员直接管理版本和启用标记。 */
public interface ProblemTestDataService {

    /** 保存指定版本；版本已存在时替换内容并保留原启用标记。 */
    Long saveTestData(Long problemId, Integer version, MultipartFile file, String remark);

    /** 设置启用标记；启用一个版本时关闭同题其他版本。 */
    void setTestDataActive(Long problemId, Integer version, boolean active);

    /** 删除指定版本，不限制其启用状态和历史引用。 */
    void deleteTestData(Long problemId, Integer version);

    /** 获取指定版本的原始 ZIP。 */
    Resource getArchive(Long problemId, Integer version);

    /** 获取当前启用数据，并确认解压文件仍然可读。 */
    ProblemTestData requireActiveTestData(Long problemId);
}
