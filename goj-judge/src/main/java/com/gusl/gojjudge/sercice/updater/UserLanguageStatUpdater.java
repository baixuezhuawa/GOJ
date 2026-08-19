package com.gusl.gojjudge.sercice.updater;

import com.gusl.common.constant.JudgingConstant;
import com.gusl.gojjudge.mapper.UserLanguageStatMapper;
import com.gusl.gojjudge.pojo.entity.SubmissionFinalizedContext;
import com.gusl.gojjudge.sercice.SubmissionFinalizedUpdater;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户语言统计更新器，按照普通提交终态累加语言使用数量和通过数量。
 */
@Component
@RequiredArgsConstructor
public class UserLanguageStatUpdater implements SubmissionFinalizedUpdater {

    private final UserLanguageStatMapper userLanguageStatMapper;

    /**
     * 累加一次已完成提交对应的语言统计。
     *
     * @param context 提交终态上下文
     */
    @Override
    public void update(SubmissionFinalizedContext context) {
        Long acceptedIncrement = JudgingConstant.ACCEPTED.equals(context.getFinalStatus())
                ? 1L
                : 0L;
        userLanguageStatMapper.increase(
                context.getUserId(),
                context.getLanguage(),
                acceptedIncrement
        );
    }
}
