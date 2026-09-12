package com.gusl.gojserver.controller.system;

import com.gusl.common.common.BaseController;
import com.gusl.common.common.Result;
import com.gusl.gojserver.pojo.dto.AddProblem2ContestDto;
import com.gusl.gojserver.pojo.dto.ContestDto;
import com.gusl.gojserver.service.ContestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理员-比赛管理")
@RestController
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequestMapping("/admin/contest")
@RequiredArgsConstructor
public class AdminContestController extends BaseController {

    private final ContestService contestService;

    /**
     * 创建比赛草稿
     */
    @Operation(summary = "创建比赛")
    @PostMapping("create-contest")
    public Result createContest(@Valid @RequestBody ContestDto contestDto){
        Long contestId = contestService.createContestDraft(contestDto);
        return success("操作成功", contestId);
    }

    /**
     * 添加题目到比赛
     */
    @Operation(summary = "添加题目到比赛")
    @PostMapping("/add-problem-to-contest")
    public Result addProblemToContest(@Valid @RequestBody AddProblem2ContestDto vo){
        contestService.addProblem2Contest(vo);
        return success();
    }

    /**
     * 推送比赛上线
     */
    @Operation(summary = "推送比赛上线")
    @PostMapping("/schedule/{contestId}")
    public Result scheduleContest(@PathVariable Long contestId){
        contestService.scheduleContest(contestId);
        return success();
    }

    /**
     * 修改比赛信息
     */
    @Operation(summary = "修改比赛信息")
    @PutMapping("/contest/{contestId}")
    public Result updateContestInfo(
            @PathVariable @NotNull Long contestId,
            @Valid @RequestBody ContestDto dto
    ){
        contestService.updateContestInfo(contestId, dto);
        return success();
    }

    @Operation(summary = "删除草稿阶段比赛题目")
    @DeleteMapping("/contest/{contestId}/{problemId}")
    public Result deleteProblemFromContest(
            @PathVariable Long contestId,
            @PathVariable Long problemId
    ){
        contestService.deleteContestProblem(contestId, problemId);
        return success("操作成功");
    }

    @Operation(summary = "删除比赛")
    @DeleteMapping("/contest/{contestId}")
    public Result deleteContest(@PathVariable Long contestId){
        contestService.deleteContest(contestId);
        return success("操作成功");
    }

}
