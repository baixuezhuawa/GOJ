package com.gusl.gojserver.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gusl.common.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 可复用的题目标签实体。 */
@io.swagger.v3.oas.annotations.tags.Tag(name = "标签实体")
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("tag")
public class Tag extends BaseEntity {

    /** 主键 id。 */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "主键 id")
    private Long id;

    /** 标签名称，业务上要求唯一。 */
    @Schema(description = "标签名称")
    private String tagName;
}
