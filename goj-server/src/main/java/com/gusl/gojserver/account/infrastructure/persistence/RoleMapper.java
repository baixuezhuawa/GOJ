package com.gusl.gojserver.account.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gusl.gojserver.account.domain.model.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


import java.util.Set;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    Set<String> getRoleCodeByUserId(@Param("userId") Long userId);
}
