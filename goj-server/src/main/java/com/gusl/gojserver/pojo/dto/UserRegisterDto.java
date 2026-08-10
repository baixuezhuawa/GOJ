package com.gusl.gojserver.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.time.LocalDateTime;

@Tag(name = "注册请求对象")
@Data
public class UserRegisterDto {

    @Schema(description = "账号")
    private String username;

    @Schema(description = "密码")
    private String password;

    @Schema(description = "再次确认密码")
    private String confirmPassword;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "性别")
    private Integer gender;

    @Schema(description = "生日")
    private LocalDateTime birthdate;

    @Schema(description = "手机号")
    private String phoneNumber;

}
