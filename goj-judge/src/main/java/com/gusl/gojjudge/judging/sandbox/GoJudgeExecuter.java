package com.gusl.gojjudge.judging.sandbox;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.gusl.gojjudge.judging.exception.SystemErrorException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@RequiredArgsConstructor
public class GoJudgeExecuter {

    private final GoJudgeClient goJudgeClient;

    public SandBoxResult execute(JSONObject request){

        SandBoxResult sandBoxResult = new SandBoxResult();

        try {
            JSONArray response = goJudgeClient.run(request);

            // Feign 的消息转换器可能将 JSONArray 元素反序列化为 LinkedHashMap，统一转回 Fastjson 对象。
            JSONObject result = JSONObject.parseObject(JSON.toJSONString(response.getFirst()));

            sandBoxResult.setStatus(result.getString("status"));

            sandBoxResult.setTime(result.getLong("time"));

            sandBoxResult.setMemory(result.getLong("memory"));

            sandBoxResult.setRunTime(result.getLong("runTime"));
            sandBoxResult.setExitStatus(result.getInteger("exitStatus"));

            Map<String, String> files = new HashMap<>();
            result.getJSONObject("files").forEach((k, v) ->
                    files.put(k, (String) v)
            );
            sandBoxResult.setFiles(files);

            // 先判断是否有 fileIds 这个字段
            if(result.containsKey("fileIds")) {
                Map<String, String> fileIds = new HashMap<>();
                result.getJSONObject("fileIds").forEach((k, v) ->
                        fileIds.put(k, (String) v)
                );
                sandBoxResult.setFileIds(fileIds);
            }else {
                sandBoxResult.setFileIds(Map.of());
            }

        } catch (Exception e) {
            throw new SystemErrorException("沙箱响应异常: " + e);
        }

        return sandBoxResult;
    }

    public void deleteCacheFile(String fileId){
        if(fileId != null){
            goJudgeClient.deleteFile(fileId);
        }
    }

}
