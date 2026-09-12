package com.gusl.gojserver.config.properties.entity;

import lombok.Data;

@Data
public class SupportedLanguageProperties {
     private String code;

     private boolean enabled;

     private String displayName;
}
