package com.gusl.gojserver.contest.application.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/** 单题成绩，attempt 为首次 AC 前计罚次数，未通过时 acceptedMinute 为 -1。 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProblemState{
    private int attempt;

    private boolean accepted;

    private int acceptedMinute;
}
