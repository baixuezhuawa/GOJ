package com.gusl.gojjudge.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RunResult {

    private String status;

    private Integer exitStatus;

    private Long timeNan;

    private Long memoryByte;

    private Long runTimeNan;

    private String stdout;

    private String stderr;

}
