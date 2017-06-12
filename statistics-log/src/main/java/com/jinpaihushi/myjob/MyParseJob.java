package com.jinpaihushi.myjob;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import com.jinpaihushi.parse.Parse114Log;
import com.jinpaihushi.parse.ParseWxLog;
import com.jinpaihushi.parse.ParseYykLog;

public class MyParseJob {
    private Logger logger = Logger.getLogger(getClass());

    @Autowired
    private ParseWxLog parseWxLog;

    @Autowired
    private Parse114Log parse114Log;

    @Autowired
    private ParseYykLog parseYykLog;

    public void parseLogJob() {
        logger.info("开始解析微信公众号日志");
        parseWxLog.readFileByLines();
        logger.info("解析微信公众号日志结束");
        logger.info("开始114日志");
        parse114Log.readFileByLines();
        logger.info("解析114结束");
        logger.info("开始医养康日志");
        parseYykLog.readFileByLines();
        logger.info("解析医养康结束");
    }
}
