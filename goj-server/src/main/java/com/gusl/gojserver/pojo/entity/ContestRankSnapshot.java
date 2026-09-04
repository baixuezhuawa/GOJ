package com.gusl.gojserver.pojo.entity;

import com.gusl.gojserver.pojo.vo.ContestRankingRowVo;
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

    private List<ContestRankingRowVo> rows;

    private LocalDateTime generatedAt;
}