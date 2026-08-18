package com.gusl.gojserver.controller.system;

import com.gusl.common.common.BaseController;
import com.gusl.common.common.PageQuery;
import com.gusl.common.common.PageResult;
import com.gusl.common.common.Result;
import com.gusl.common.pojo.entity.JudgeTask;
import com.gusl.gojserver.pojo.vo.JudgeTaskListVo;
import com.gusl.gojserver.service.JudgeTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 测评任务管理接口，供管理员查询任务和发起死亡任务重测。
 */
@Tag(name = "测评任务管理")
@PreAuthorize("hasAuthority('submission:manage')")
@RequiredArgsConstructor
@RestController
@RequestMapping("/admin/judge-task")
public class AdminJudgeTaskController extends BaseController {

    private final JudgeTaskService judgeTaskService;

    @Operation(summary = "根据状态获取测评任务列表")
    @GetMapping("/list/{status}")
    public Result list(
            @PathVariable String status,
            @ModelAttribute PageQuery page
    ){
        PageResult<JudgeTaskListVo> list = judgeTaskService.getListByStatus(status, page);
        return success("操作成功", list);
    }

    @Operation(summary = "任务详情")
    @GetMapping("/{taskId}")
    public Result getJudgeTaskById(@PathVariable Long taskId){
        JudgeTask judgeTask = judgeTaskService.getById(taskId);
        return success("操作成功", judgeTask);
    }


    @Operation(summary = "重试死亡任务")
    @PostMapping("{taskId}/retry")
    public Result retryDeadJudgeTask(@PathVariable Long taskId){
        Long newTaskId = judgeTaskService.retryDeadTask(taskId);
        return success("已创建新的重测任务", newTaskId);
    }

}
