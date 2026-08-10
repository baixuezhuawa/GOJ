package com.gusl.gojserver.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Set;

@Tag(name = "当前登录用户信息")
@Data
@AllArgsConstructor
public class CurrentUserVo {

    @Schema(description = "用户id")
    private Long userId;

    @Schema(description = "账号")
    private String username;

    @Schema(description = "权限集合")
    private Set<String> permissions;

}
