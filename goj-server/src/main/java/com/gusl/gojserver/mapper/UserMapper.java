package com.gusl.gojserver.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gusl.gojserver.pojo.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;


@Mapper
public interface UserMapper  extends BaseMapper<User> {

    @Select("select * from sys_user where username = #{username}")
    User getUserByUsername(@Param("username") String username);

    @Select("select count(username) from sys_user where username = #{username}")
    int CountUserByUsername(String username);
}
