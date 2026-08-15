package com.gusl.gojjudge.exception;

/**
 * 编译阶段错误。
 *
 * <p>用于表达编译流程中的业务异常；真正的异常状态落库仍由 JudgeService 统一完成。</p>
 */
public class CompileErrorException extends RuntimeException {

    /** 创建没有详细信息的编译异常。 */
    public CompileErrorException(){
        super();
    }

    /**
     * 创建带有错误信息的编译异常。
     *
     * @param msg 编译错误说明
     */
    public CompileErrorException(String msg){
        super(msg);
    }



}
