package com.gusl.gojserver.config.properties;

import com.gusl.gojserver.config.properties.entity.FileProperties;
import com.gusl.gojserver.config.properties.entity.PaginationProperties;
import com.gusl.gojserver.config.properties.entity.ProfileProperties;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "goj")
public class SysProperties {

    private String dataRoot;

    private ProfileProperties profile;

    private PaginationProperties pagination;

    private FileProperties file;
}
