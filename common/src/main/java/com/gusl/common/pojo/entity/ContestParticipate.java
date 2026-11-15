package com.gusl.common.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ContestParticipate {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long contestId;

    private String participateType;

    private LocalDateTime registerTime;
}
