package com.gusl.gojserver.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(name = "提交Dto")
@Data
public class Submission2JudgeDto {

    @Schema(description = "题目id", example = "1")
    private Long problemId;

    @Schema(description = "代码语言", example = "java11")
    private String language;

    @Schema(description = "源代码", example = "import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner scanner = new Scanner(System.in);\n        long a = scanner.nextLong();\n        long b = scanner.nextLong();\n        System.out.println(a + b);\n    }\n}")
    private String sourceCode;

    @Schema(description = "比赛id", example = "1")
    private Long contestId;

}
