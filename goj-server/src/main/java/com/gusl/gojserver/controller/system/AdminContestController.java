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

@Tag(name = "比赛管理")
@RestController
@RequestMapping("/admin/contest")
@RequiredArgsConstructor
public class AdminContestController extends BaseController {

    private final ContestService contestService;

    /**
     * 创建比赛草稿
     */
    @Operation(summary = "创建比赛")
    @PreAuthorize("hasAuthority('contest:manage')")
    @PostMapping("create-contest")
    public Result createContest(@Valid @RequestBody ContestDto contestDto){
        contestService.createContestDraft(contestDto);
        return success();
    }

    /**
     * 添加题目到比赛
     */
    @Operation(summary = "添加题目到比赛")
    @PreAuthorize("hasAuthority('contest:manage')")
    @PostMapping("/add-problem-to-contest")
    public Result addProblemToContest(@Valid @RequestBody AddProblem2ContestDto vo){
        contestService.addProblem2Contest(vo);
        return success();
    }

    /**
     * 推送比赛上线
     */
    @Operation(summary = "推送比赛上线")
    @PreAuthorize("hasAuthority('contest:manage')")
    @PostMapping("/schedule/{contestId}")
    public Result scheduleContest(@PathVariable Long contestId){
        contestService.scheduleContest(contestId);
        return success();
    }

    /**
     * 修改比赛信息
     */
    @PutMapping("/contest/{contestId}")
    public Result updateContestInfo(
            @PathVariable @NotNull Long contestId,
            @Valid @RequestBody ContestDto dto
    ){
        contestService.updateContestInfo(contestId, dto);
        return success();
    }

}
