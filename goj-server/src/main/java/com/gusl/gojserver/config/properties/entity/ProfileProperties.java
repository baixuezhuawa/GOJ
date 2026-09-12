package com.gusl.gojserver.config.properties.entity;

import lombok.Data;

@Data
public class ProfileProperties {

    /** 最近几次提交数 */
    private Integer recentSubmissionSize;

    /** 内容完全一样的重复提交的限制时间 */
    private Integer frequencyOfRepeatedSubmissions;

}
