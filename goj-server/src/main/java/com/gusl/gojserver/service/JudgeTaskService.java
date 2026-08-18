package com.gusl.gojserver.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gusl.common.common.PageQuery;
import com.gusl.common.common.PageResult;
import com.gusl.common.pojo.entity.JudgeTask;
import com.gusl.gojserver.pojo.vo.JudgeTaskListVo;

public interface JudgeTaskService extends IService<JudgeTask> {

    /**
     * 获取任务列表
     * @param status 任务状态
     * @param pageQuery 分页
     * @return JudgeTaskListVo
     */
    PageResult<JudgeTaskListVo> getListByStatus(String status, PageQuery pageQuery);


    /**
     * 重启死亡的任务
     * @param taskId 死亡任务id
     * @return 新创建的任务 id
     */
    Long retryDeadTask(Long taskId);
}
