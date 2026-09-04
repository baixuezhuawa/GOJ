package com.gusl.gojserver.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gusl.common.common.PageQuery;
import com.gusl.common.common.PageResult;
import com.gusl.gojserver.pojo.dto.ProblemDraftDto;
import com.gusl.gojserver.pojo.dto.ProblemPageListDto;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.gojserver.pojo.dto.UpdateProblemDraftDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.pojo.vo.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ProblemService extends IService<Problem> {

    /** 获取问题列表 */
    PageResult<ProblemPageListVo> getProblemList(ProblemPageListDto dto, LoginUser loginUser);

    /** 获取问题具体信息 */
    ProblemInfoVo getProblemInfoById(Long id);

    /**
     * 创建题目草稿并上传测试数据。
     *
     * @return 新建题目 id
     */
    Long uploadProblemByUser(ProblemDraftDto draft, MultipartFile data, LoginUser loginUser) throws IOException;

    /** 获取当前用户自己创建的问题列表 */
    PageResult<ProblemDraftListVo> getUploadProblemList(PageQuery pageQuery, LoginUser loginUser);

    /** 需改草稿阶段的问题 */
    void updateMyProblemDraft(UpdateProblemDraftDto problemDraftDto, LoginUser loginUser);

    void reUploadProblem(Long problemId, LoginUser loginUser);

    void deleteMyProblemDraft(Long problemId, LoginUser loginUser);

    /** 管理员停用题目或将停用题目恢复到准备状态。 */
    void setProblemStatus(Long problemId, Integer status);


    UploadProblemDetailVo getUploadProblemDetail(Long problemId, LoginUser loginUser);

    /** 作者在已发布和准备状态之间切换自己的题目。 */
    void updateMyProblemStatus(Long problemId, Integer status, LoginUser loginUser);

    /** 获取管理员题目列表，包含全部题目状态。 */
    PageResult<AdminProblemPageListVo> getProblemListByAdmin(ProblemPageListDto dto, LoginUser loginUser);

    TotalProblemInfoVo getTotalProblemInfo(Long problemId);

    void setProblemDifficultByAdmin(Long problemId, Integer difficult);

    void addProblemTag(Long problemId, Long tagId);

    void deleteProblemTag(Long problemId, Long tagId);
}
