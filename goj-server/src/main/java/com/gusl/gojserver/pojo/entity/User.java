package com.gusl.gojserver.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gusl.common.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;


@Schema(description = "用户实体类")
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("sys_user")
public class User extends BaseEntity {

    /**
     * 用户id
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "用户id")
    private Long id;

    /**
     * 账号
     */
    @Schema(description = "账号")
    private String username;

    /**
     * 密码
     */
    @Schema(description = "密码")
    private String password;

    /**
     * 账号状态
     */
    @Schema(description = "状态 启用: 1 禁用: 0")
    private Integer status;

    /**
     * 邮箱
     */
    @Schema(description = "邮箱")
    private String email;

    /**
     * 性别
     */
    @Schema(description = "性别")
    private Integer gender;

    /**
     * 出生日期
     */
    @Schema(description = "出生日期")
    private LocalDateTime birthdate;

    /**
     * 手机号
     */
    @Schema(description = "手机号")
    private String phoneNumber;

    /**
     * 头像
     */
    @Schema(description = "头像")
    private String avatar;

}
