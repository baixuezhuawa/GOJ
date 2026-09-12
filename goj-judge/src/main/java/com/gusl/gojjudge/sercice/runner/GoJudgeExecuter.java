package com.gusl.gojjudge.sercice.runner;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.gusl.gojjudge.client.GoJudgeClient;
import com.gusl.gojjudge.exception.SystemErrorException;
import com.gusl.gojjudge.pojo.entity.SandBoxResult;
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

            JSONObject result = (JSONObject) response.getFirst();

            sandBoxResult.setStatus(result.getString("status"));

            sandBoxResult.setTime(result.getLong("time"));

            sandBoxResult.setMemory(result.getLong("memory"));

            sandBoxResult.setRunTime(result.getLong("runTime"));

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
                sandBoxResult.setFiles(fileIds);
            }else {
                sandBoxResult.setFiles(Map.of());
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
