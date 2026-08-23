package com.gusl.common.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContestParticipate {

    private Long id;

    private Long userId;

    private Long contestId;

    private String participateType;

    private LocalDateTime registerTime;
}
