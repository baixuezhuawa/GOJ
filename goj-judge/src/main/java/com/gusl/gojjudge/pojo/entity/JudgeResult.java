package com.gusl.gojjudge.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 测评结果
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JudgeResult {

    private String status;

    private String judgeMsg;

    private Integer timeMs;

    private Integer memoryKb;

    private Integer score;
}
