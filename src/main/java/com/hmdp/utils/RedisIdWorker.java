package com.hmdp.utils;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    private static final long BEGIN_TIMESTAMP = 1640995200L;

    private StringRedisTemplate stringRedisTemplate;
    public long nextId(String keyPrefix){
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSeconds =   now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSeconds  - BEGIN_TIMESTAMP;
        //s恒成序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long cnt = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + date);

        //拼接返回
        return timeStamp << 32 | cnt;
    }
}
