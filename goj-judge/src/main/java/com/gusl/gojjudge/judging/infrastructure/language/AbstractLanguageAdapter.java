package com.gusl.gojjudge.judging.infrastructure.language;

import com.alibaba.fastjson2.JSONObject;
import com.gusl.gojjudge.judging.infrastructure.runner.RunContext;

import java.util.List;

/**
 * go-judge 语言适配器的公共基类。
 *
 * <p>本类定义判题流程依赖的统一契约；实现类负责组装 go-judge 的
 * {@code cmd} 请求、设置资源限制、挂载输入输出文件以及转换资源单位。
 * 适配器不负责发起 HTTP 请求、不更新提交状态，也不执行用户代码。</p>
 */
public abstract class AbstractLanguageAdapter {

    /**
     * 返回提交使用的语言编码。
     *
     * @return 与 server 语言配置和提交记录一致的语言编码
     */
    public abstract String languageCode();

    /**
     * 运行时挂载的文件名，可以是编译产物或脚本源码
     */
    public abstract String activeFileName();

    /** 判断是否需要编译 */
    public abstract boolean isNeedCompile();

    /** 创建编译请求 */
    public abstract JSONObject createCompileRequest(String sourceCode);


    /**
     * 根据一次测试点运行上下文创建 go-judge 请求体。
     *
     * @param runContext 测试输入、编译产物和运行时资源限制
     * @return 可直接发送给 go-judge {@code /run} 接口的 JSON 请求
     */
    public abstract JSONObject createRunRequest(RunContext runContext);


    /**
     * 返回运行用户程序时使用的命令参数。
     *
     * @return go-judge {@code cmd.args} 参数
     */
    protected abstract List<String> buildRunArgs();

    /**
     * 返回编译用户程序时使用的命令参数。
     *
     * @return go-judge {@code cmd.args} 参数
     */
    protected abstract List<String> buildCompileArgs();


}
