package com.gusl.common.utils;

/** 判断便捷工具 */
public class JudgmentUtil {

    /**
     * 是否为 true
     * @param conditions 条件
     * @return 有一个为 true 就为 true
     */
    public static boolean or(boolean... conditions){
        for(boolean b : conditions){
            if (b) return true;
        }
        return false;
    }


    /**
     * 是否都为true
     * @param conditions 条件
     * @return 都为 true才为 true
     */
    public static boolean and(boolean... conditions){
        for(boolean b : conditions){
            if (!b) return false;
        }
        return true;
    }


}
