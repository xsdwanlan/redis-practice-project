package com.hmdp.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @wwj
 * @date
 */
@Component
@Slf4j
public class RedisIdWorker {
    //到今年第一天的秒数
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    //序列号的位数
    private static final long COUNT_TIMESTAMP = 32L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix){//不同业务

        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond-BEGIN_TIMESTAMP;

        //生成序列号
        String today = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("irc:" + keyPrefix + ":" + today);

        //拼接，数字的位运算——或运算，因为后32位都是0了，或之后结果取决于填充的数字
        return timeStamp << COUNT_TIMESTAMP  |  count ;

    }

    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second = " + second);
    }
}
