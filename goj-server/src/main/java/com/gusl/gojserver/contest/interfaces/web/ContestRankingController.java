package com.gusl.gojserver.contest.interfaces.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gusl.common.common.*;
import com.gusl.common.constant.ContestStatus;
import com.gusl.common.pojo.entity.Contest;
import com.gusl.common.pojo.entity.ContestProblem;
import com.gusl.gojserver.contest.application.model.UserRankRow;
import com.gusl.gojserver.contest.application.ranking.RankingArchiveService;
import com.gusl.gojserver.contest.application.ranking.RankingManager;
import com.gusl.gojserver.contest.application.vo.ContestRankingVo;
import com.gusl.gojserver.contest.infrastructure.persistence.ContestMapper;
import com.gusl.gojserver.contest.infrastructure.persistence.ContestProblemMapper;
import com.gusl.gojserver.shared.application.pagination.PageFactory;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 排行榜查询入口，实时比赛读内存，归档完成后读取最终快照。 */
@RestController
@RequestMapping("/contest")
@RequiredArgsConstructor
public class ContestRankingController extends BaseController {
    private final RankingManager rankings;
    private final RankingArchiveService archive;
    private final ContestMapper contests;
    private final ContestProblemMapper problems;
    private final PageFactory pages;

    /** 对完整榜单排名后分页，保持并列名次跨页一致。 */
    @Operation(summary = "获取比赛排行榜")
    @GetMapping("/{contestId}/ranking")
    public Result getRanking(@PathVariable Long contestId, @ModelAttribute PageQuery query) {
        Contest contest = contests.selectById(contestId);
        if (contest == null || ContestStatus.DRAFT.equals(contest.getStatus())
                || ContestStatus.SCHEDULED.equals(contest.getStatus())) {
            throw new BaseException("比赛不存在或尚未开始");
        }
        if (ContestStatus.SYNC.equals(contest.getStatus())) {
            throw new BaseException("排行榜正在整理");
        }
        IPage<UserRankRow> page = pages.create(query);
        if (!ContestStatus.FINISH.equals(contest.getStatus())) {
            IPage<UserRankRow> livePage = rankings.getRankingPage(contestId, page);
            if (livePage == null) {
                // 查询期间可能刚进入归档阶段，重新读取状态，不恢复已关闭的实时榜。
                contest = contests.selectById(contestId);
                if (contest == null || !ContestStatus.FINISH.equals(contest.getStatus())) {
                    throw new BaseException("排行榜正在整理");
                }
            }
        }
        if (ContestStatus.FINISH.equals(contest.getStatus())) {
            List<UserRankRow> rows = archive.getFinalRankings(contestId);
            long offset = page.getCurrent() - 1;
            int start = offset > rows.size() / page.getSize() ? rows.size() :
                    (int) Math.min(offset * page.getSize(), rows.size());
            int end = start + (int) Math.min(page.getSize(), rows.size() - start);
            page.setTotal(rows.size());
            page.setRecords(rows.subList(start, end));
        }
        ContestRankingVo vo = new ContestRankingVo();
        vo.setContestId(contestId);
        vo.setContestStatus(contest.getStatus());
        vo.setContestProblems(problems.selectList(Wrappers.<ContestProblem>lambdaQuery()
                .eq(ContestProblem::getContestId, contestId).orderByAsc(ContestProblem::getSortOrder)));
        vo.setContestRankingRows(PageResult.of(page));
        return success("操作成功", vo);
    }
}
