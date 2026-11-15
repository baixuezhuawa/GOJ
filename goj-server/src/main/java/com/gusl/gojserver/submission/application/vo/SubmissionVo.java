package com.gusl.gojserver.submission.application.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SubmissionVo {

    private String submissionType;

    private Long submissionId;
}
