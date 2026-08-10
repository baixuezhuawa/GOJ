package com.gusl.common.common;

import com.gusl.common.constant.HttpStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result {

    private Integer code;

    private String msg;

    private Object data;

    public static Result success(){
        return new Result(HttpStatus.SUCCESS, "操作成功", null);
    }

    public static Result success(String msg){
        return new Result(HttpStatus.SUCCESS, msg, null);
    }

    public static Result success(String msg, Object data){
        return new Result(HttpStatus.SUCCESS, msg, data);
    }

    public static Result error(){
        return new Result(HttpStatus.ERROR, "操作失败, 请联系管理员", null);
    }

    public static Result error(String msg){
        return new Result(HttpStatus.ERROR, msg, null);
    }

    public static Result error(String msg, Object data){
        return new Result(HttpStatus.ERROR, msg, data);
    }
}
