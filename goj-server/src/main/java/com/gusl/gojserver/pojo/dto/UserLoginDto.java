package com.gusl.gojserver.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

@Tag(name = "用户登录请求对象")
@Data
public class UserLoginDto {

    @Schema(description = "账号", example = "admin")
    private String username;

    @Schema(description = "密码", example = "Admin@123456")
    private String password;

    @Schema(description = "验证码", example = "666")
    private String code;

}
