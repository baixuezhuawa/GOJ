package com.gusl.gojserver.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

@Tag(name = "角色实体类")
@Data
@TableName("sys_role")
public class Role {

    /**
     * 角色id
     */
    @Schema(description = "角色id")
    private Long id;

    /**
     * 角色状态
     */
    @Schema(description = "角色状态 启用: 1 禁用: 0")
    private Integer status;

    /**
     * 角色名称
     */
    @Schema(description = "角色名称")
    private String roleName;

    /**
     * 角色标识符
     */
    @Schema(description = "角色标识符")
    private String roleCode;

}
