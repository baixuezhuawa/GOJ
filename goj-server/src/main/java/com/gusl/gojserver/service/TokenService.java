package com.gusl.gojserver.service;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.gusl.common.utils.IdUtils;
import com.gusl.common.utils.StringUtils;
import com.gusl.gojserver.pojo.entity.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${security.token.secret}")
    private String secret;

    @Value("${security.token.redis-prefix}")
    private String prefix;

    @Value("${security.token.expire-minutes}")
    private long expireMinutes;

    public String createToken(LoginUser loginUser) {
        long loginTime = System.currentTimeMillis();
        long expireTime = loginTime + TimeUnit.MINUTES.toMillis(expireMinutes);

        // 服务端登录记录编号
        String loginId = IdUtils.fastSimpleUUID();

        loginUser.setLoginId(loginId);
        loginUser.setLoginTime(loginTime);
        loginUser.setExpireTime(expireTime);

        byte[] key = secret.getBytes(StandardCharsets.UTF_8);

        // JWT 是最终返回给前端的字符串
        String jwtToken = JWT.create()
                .setPayload("loginId", loginId)
                .setIssuedAt(new Date(loginTime))
                .setExpiresAt(new Date(expireTime))
                .setKey(key)
                .sign();

        // Redis 使用 loginId 作为 key 的一部分
        redisTemplate.opsForValue().set(prefix + loginId, loginUser, expireMinutes, TimeUnit.MINUTES);

        return jwtToken;
    }

    public Optional<LoginUser> getLoginUser(String jwtToken) {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);

        JWT jwt;
        try{
          jwt = JWT.of(jwtToken).setKey(key);
           // verify() 校验签名
            // validate(0) 校验 nbf、exp 等时间字段，0 表示不允许时间误差
          if (!jwt.verify() || !jwt.validate(0)) {
              return Optional.empty();
            }
            jwt = JWTUtil.parseToken(jwtToken);
        } catch (RuntimeException e){
            return Optional.empty();
        }

        String loginId = (String) jwt.getPayload("loginId");

        if(StringUtils.isEmpty(loginId)){
            return Optional.empty();
        }

        Object value = redisTemplate.opsForValue().get(prefix + loginId);

        if(value instanceof LoginUser loginUser){
            return Optional.of(loginUser);
        }

        return Optional.empty();
    }

    public void deleteLoginUser(String loginId) {
        if (StringUtils.isEmpty(loginId)) {
            return;
        }
        redisTemplate.delete(prefix + loginId);
    }
}
