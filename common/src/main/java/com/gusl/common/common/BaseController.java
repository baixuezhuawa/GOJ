package com.gusl.common.common;

public class BaseController {

    public Result success(){
        return Result.success();
    }

    public Result success(String msg){
        return Result.success(msg);
    }

    public Result success(String msg, Object data){
        return Result.success(msg, data);
    }

    public Result error(){
        return Result.error();
    }

    public Result error(String msg){
        return Result.error(msg);
    }

    public Result error(String msg, Object data){
        return Result.error(msg, data);
    }
}
