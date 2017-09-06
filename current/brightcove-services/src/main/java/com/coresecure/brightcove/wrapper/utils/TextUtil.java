package com.coresecure.brightcove.wrapper.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TextUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(TextUtil.class);

    public static boolean isEmpty(final String str) {
        return (str == null || str.trim().length() == 0);
    }

    public static boolean notEmpty(final String str) {
        return (str != null && str.trim().length() > 0);
    }


}
