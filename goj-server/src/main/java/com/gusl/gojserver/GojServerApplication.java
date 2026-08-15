package com.gusl.gojserver;

import com.gusl.gojserver.config.properties.SubmissionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SubmissionProperties.class)
public class GojServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GojServerApplication.class, args);
    }

}
