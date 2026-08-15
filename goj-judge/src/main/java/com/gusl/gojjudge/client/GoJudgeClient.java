package com.gusl.gojjudge.client;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * go-judge 沙箱 HTTP 客户端。
 *
 * <p>Judge 只通过该客户端提交编译和运行请求。该接口不包含测评状态逻辑，
 * 也不在本地执行用户代码。</p>
 */
@FeignClient(name = "go-judge-sandbox", url = "${goj.judge.sandbox-base-url}")
public interface GoJudgeClient {

    /**
     * 调用 go-judge 的统一执行接口。
     *
     * @param request 编译或运行请求体
     * @return go-judge 返回的命令结果数组
     */
    @PostMapping(value = "/run", consumes = MediaType.APPLICATION_JSON_VALUE)
    JSONArray run(@RequestBody JSONObject request);

    /**
     * 删除沙箱中由 {@code copyOutCached} 创建的编译产物。
     *
     * @param fileId 沙箱缓存文件 ID
     */
    @DeleteMapping("/file/{fileId}")
    void deleteFile(@PathVariable("fileId") String fileId);

}
