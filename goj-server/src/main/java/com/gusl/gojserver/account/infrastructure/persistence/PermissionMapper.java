package com.gusl.gojserver.account.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gusl.gojserver.account.domain.model.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Set;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {

    Set<String> getPermissionCodesByUserId(@Param("userId") Long userId);

    @Select("select permission_code from sys_permission")
    Set<String> getAllPermission();

}
