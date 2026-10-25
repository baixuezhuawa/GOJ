package com.gusl.gojserver.contest.application.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.util.List;

/** 用户成绩行，罚时按分钟计，题格列表直接使用比赛 sortOrder 下标。 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserRankRow {
    private int rank;

    private Long userId;

    private String username;

    private int penalty;

    private int acceptedCount;

    private List<ProblemState> states;

}
