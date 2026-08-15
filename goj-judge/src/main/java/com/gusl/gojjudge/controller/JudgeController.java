package com.gusl.gojjudge.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Judge HTTP 入口预留类。
 *
 * <p>当前测评任务通过 Redis 队列进入 Worker，因此暂不提供面向用户的 HTTP 接口；
 * 保留该类是为了后续增加内部健康检查或管理接口时有明确边界。</p>
 */
@RestController
@RequestMapping("/judge")
public class JudgeController {


}
