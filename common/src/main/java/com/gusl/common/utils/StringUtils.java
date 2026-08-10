package com.gusl.common.utils;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

public class StringUtils {

    /** Spring应用上下文环境 */
    private static ConfigurableListableBeanFactory beanFactory;

    public static boolean isEmpty(String... str){
        if(str == null || str.length == 0){
            return true;
        }
        for(String s : str){
            if(s == null || s.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isNotEmpty(String... str) {
        // 判断这个字符串是否为null
        if(str == null) {
            return false;
        }
        // 判断里面是否每个元素都不为空
        for(String s : str){
            if(isEmpty(s)){
                return false;
            }
        }
        return true;
    }
}
