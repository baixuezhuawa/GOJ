package com.gusl.gojserver.config.properties;

import com.gusl.gojserver.config.properties.entity.SupportedLanguageProperties;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "goj.submission")
public class SubmissionProperties {

    private Integer maxCodeLength;

    private List<SupportedLanguageProperties> languages;

}