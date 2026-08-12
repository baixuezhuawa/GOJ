package com.gusl.gojserver.config.properties;

import lombok.Data;

@Data
public class SupportedLanguageProperties {
     private String code;

     private boolean enabled;

     private String displayName;
}
