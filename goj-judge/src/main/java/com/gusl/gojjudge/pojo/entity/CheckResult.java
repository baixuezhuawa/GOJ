package com.gusl.gojjudge.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CheckResult {

    private boolean match;

    private String msg;

    public static CheckResult accepted(){
        return new CheckResult(true, null);
    }

    public static CheckResult failed(String msg){
        return new CheckResult(false, msg);
    }

}
