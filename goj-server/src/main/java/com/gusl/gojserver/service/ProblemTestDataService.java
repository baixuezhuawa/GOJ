package com.gusl.gojserver.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gusl.common.pojo.entity.ProblemTestData;
import com.gusl.gojserver.pojo.entity.LoginUser;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ProblemTestDataService extends IService<ProblemTestData> {

    /**
     * 上传测试数据
     * @param problemId 问题id
     * @param file 测试数据.zip
     * @param loginUser 登录用户
     */
    void uploadTestData(Long problemId, MultipartFile file, LoginUser loginUser) throws IOException;

    /**
     * 草稿阶段替换测试数据
     */
    void updateTestDataWithdraw(Long problemId, MultipartFile data, LoginUser loginUser) throws IOException;

    /** 获取测试数据下一个版本 */
    int nextVersion(Long problemId);

    /** 管理员上传测试数据 */
    void uploadTestDataByAdmin(Long problemId, MultipartFile data, String remark);


    void enableProblemTestDataVersion(Long problemId, Integer version);

    void deleteProblemTestDataVersion(Long problemId, Integer version);
}
