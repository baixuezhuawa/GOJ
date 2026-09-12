package com.gusl.gojjudge.pojo.entity;

import lombok.Data;

import java.util.Map;

/**
 * 沙箱请求响应体
 */
@Data
public class SandBoxResult {

    private String status;

    private Integer exitStatus;

    private Long time;

    private Long memory;

    private Long runTime;

    private Map<String, String> files;

    private Map<String, String> fileIds;

}
