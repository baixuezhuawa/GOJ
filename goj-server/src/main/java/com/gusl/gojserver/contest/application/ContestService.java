package com.gusl.gojserver.contest.application;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gusl.common.common.PageQuery;
import com.gusl.common.common.PageResult;
import com.gusl.common.pojo.entity.Contest;
import com.gusl.gojserver.contest.application.dto.AddProblem2ContestDto;
import com.gusl.gojserver.contest.application.dto.ContestDto;
import com.gusl.gojserver.account.infrastructure.security.LoginUser;
import com.gusl.gojserver.contest.application.vo.ContestDetailVo;
import com.gusl.gojserver.contest.application.vo.ContestListVo;
import com.gusl.gojserver.contest.application.vo.ContestProblemDetailVo;

public interface ContestService extends IService<Contest> {

    Long createContestDraft(ContestDto contestDto);

    void addProblem2Contest(AddProblem2ContestDto vo);

    void scheduleContest(Long contestId);

    PageResult<ContestListVo> getUnfinishContest(PageQuery pageQuery, LoginUser loginUser);

    PageResult<ContestListVo> getFinishContest(PageQuery pageQuery, LoginUser loginUser);

    ContestDetailVo getContestDetail(Long contestId);

    void updateContestInfo(Long contestId, ContestDto dto);

    ContestProblemDetailVo getContestProblemDetail(Long contestId, String problemCode);

    void deleteContestProblem(Long contestId, Long problemId);

    void deleteContest(Long contestId);

}
