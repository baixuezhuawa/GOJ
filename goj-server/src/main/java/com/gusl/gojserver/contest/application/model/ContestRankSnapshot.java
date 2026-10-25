package com.gusl.gojserver.contest.application.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ContestRankSnapshot {

    private Long contestId;

    private List<UserRankRow> rows;

    private LocalDateTime generatedAt;
}