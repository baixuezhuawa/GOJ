package com.gusl.gojjudge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * GOJ Judge Worker 启动类。
 *
 * <p>启动 Spring 容器、扫描 MyBatis Mapper，并启用 go-judge Feign 客户端。
 * 实际用户代码执行仍在独立沙箱中完成。</p>
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.gusl.gojjudge.client")
@MapperScan("com.gusl.gojjudge.mapper")
public class GojJudgeApplication {

    /**
     * 启动 Judge Worker 应用。
     *
     * @param args Spring Boot 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GojJudgeApplication.class, args);
    }

}
