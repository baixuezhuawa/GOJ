package com.gusl.gojserver.submission.application.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/** 提交状态响应，仅提供轮询需要的判题结果。 */
@Data
public class SubmissionStatusVo {
    private Long id;

    private Integer version;

    private String status;

    private Integer score;

    private Integer timeMs;

    private Integer memoryKb;

    private String compilerMsg;

    private String judgeMsg;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime judgeStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime judgeEndTime;
}
