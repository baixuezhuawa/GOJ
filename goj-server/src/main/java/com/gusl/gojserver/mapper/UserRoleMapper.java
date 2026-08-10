package com.gusl.gojserver.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserRoleMapper {

    @Insert("insert into sys_user_role (user_id, role_id) value (#{userId}, #{roleId})")
    void insert(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
