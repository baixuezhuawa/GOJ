package com.gusl.gojserver.shared.infrastructure.config.properties.entity;

import lombok.Data;

@Data
public class SupportedLanguageProperties {
     private String code;

     private boolean enabled;

     private String displayName;
}
