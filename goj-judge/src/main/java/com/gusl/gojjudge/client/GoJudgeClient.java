package com.gusl.gojjudge.client;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "go-judge-sandbox", url = "${goj.judge.sandbox-base-url}")
public interface GoJudgeClient {

    @PostMapping(value = "/run", consumes = MediaType.APPLICATION_JSON_VALUE)
    JSONArray run(@RequestBody JSONObject request);

    /**
     * Deletes a file cached by copyOutCached in the sandbox.
     */
    @DeleteMapping("/file/{fileId}")
    void deleteFile(@PathVariable("fileId") String fileId);

}
