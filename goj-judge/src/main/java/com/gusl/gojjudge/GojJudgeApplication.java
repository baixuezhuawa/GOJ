package com.gusl.gojjudge;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.gusl.gojjudge.client")
@MapperScan("com.gusl.gojjudge.mapper")
public class GojJudgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(GojJudgeApplication.class, args);
    }

}
