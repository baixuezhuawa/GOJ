package com.gusl.gojserver.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gusl.gojserver.pojo.dto.ProblemDraftDto;
import com.gusl.gojserver.pojo.dto.ProblemPageListDto;
import com.gusl.common.pojo.entity.Problem;
import com.gusl.gojserver.pojo.dto.UpdateProblemDraftDto;
import com.gusl.gojserver.pojo.entity.LoginUser;
import com.gusl.gojserver.pojo.vo.ProblemDraftInfoVo;
import com.gusl.gojserver.pojo.vo.ProblemInfoVo;
import com.gusl.gojserver.pojo.vo.ProblemPageListVo;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ProblemService extends IService<Problem> {

    /** 获取问题列表 */
    List<ProblemPageListVo> getProblemList(ProblemPageListDto dto);

    /** 获取问题具体信息 */
    ProblemInfoVo getProblemInfoById(Long id);

    /** 创建题目草稿 */
    void uploadProblemByUser(ProblemDraftDto draft, MultipartFile data, LoginUser loginUser) throws IOException;

    /** 获取当前用户自己创建的问题列表 */
    List<ProblemDraftInfoVo> getUploadProblemList(LoginUser loginUser);

    /** 需改草稿阶段的问题 */
    void updateMyProblemDraft(UpdateProblemDraftDto problemDraftDto, LoginUser loginUser);

    void reUploadProblem(Long problemId, LoginUser loginUser);

    void deleteMyProblemDraft(Long problemId, LoginUser loginUser);

}
