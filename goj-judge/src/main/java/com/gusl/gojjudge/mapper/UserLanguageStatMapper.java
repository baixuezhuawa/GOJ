package com.gusl.gojjudge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gusl.common.pojo.entity.UserLanguageStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户语言统计持久化访问接口。
 */
@Mapper
public interface UserLanguageStatMapper extends BaseMapper<UserLanguageStat> {

    /**
     * 原子累加用户指定语言的提交数量和通过数量。
     *
     * @param userId 用户 id
     * @param language 语言编码
     * @param acceptedIncrement Accepted 数量增量
     * @return 受影响行数
     */
    int increase(
            @Param("userId") Long userId,
            @Param("language") String language,
            @Param("acceptedIncrement") Long acceptedIncrement
    );
}
