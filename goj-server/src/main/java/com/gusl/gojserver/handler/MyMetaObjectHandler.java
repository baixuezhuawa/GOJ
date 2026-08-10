package com.gusl.gojserver.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 配置MybatisPlus的字段自动填充
 */
@Component
@RequiredArgsConstructor
public class MyMetaObjectHandler implements MetaObjectHandler {

    private final HttpServletRequest request;

    /**
     * 判断是否为其他地方发送的请求, 如果是则被排除掉
     *
     * @return 是否排除
     */
    private boolean isExclude() {

        return true;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        if (isExclude()) {
            this.strictInsertFill(metaObject, "createBy", String.class, String.valueOf(getLoginUserId()));
        }
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        if (isExclude()) {
            this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        }
        this.strictUpdateFill(metaObject, "updateBy", String.class, String.valueOf(getLoginUserId()));
    }

    /**
     * 返回当前登录用户的id
     * @return 等于用户id
     */
    public Long getLoginUserId(){
        // 利用aop将用户id等字段存入上下文中
        return 1L;
    }

}