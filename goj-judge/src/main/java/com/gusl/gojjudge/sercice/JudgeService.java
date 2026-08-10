package com.gusl.gojjudge.sercice;

import java.io.IOException;

public interface JudgeService {

    void judge(Long taskId) throws IOException;
}
