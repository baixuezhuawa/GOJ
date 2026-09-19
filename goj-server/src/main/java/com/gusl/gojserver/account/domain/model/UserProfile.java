package com.gusl.gojserver.account.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserProfile {

    private String email;

    private Integer gender;

    private String phoneNumber;

    private String avatar;

    private LocalDateTime birthdate;
}
