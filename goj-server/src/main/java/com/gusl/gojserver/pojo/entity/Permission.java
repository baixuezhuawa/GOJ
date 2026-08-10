package com.gusl.gojserver.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@TableName("sys_permission")
public class Permission {

    /**
     * 权限id
     */
    private Long id;

    /**
     * 权限名称
     */
    @Schema(description = "权限名称")
    private String permissionName;

    /**
     * 角色标识符
     */
    @Schema(description = "权限标识符")
    private String permissionCode;
}
