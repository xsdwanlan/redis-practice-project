package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @wwj
 * @date
 */
public class SimpleRedisLock implements ILock{
    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static{//写成静态代码块，类加载就可以完成初始定义，就不用每次释放锁都去加载这个，性能提高咯
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));//设置脚本位置
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    public SimpleRedisLock(String name,StringRedisTemplate stringRedisTemplate){
        this.name=name;
        this.stringRedisTemplate=stringRedisTemplate;
    }


    @Override
    public boolean tryLock(long timeoutSec) {
        String key = KEY_PREFIX + name;
        //value的话一般设置为哪个线程持有该锁即可
        //获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, threadId, timeoutSec, TimeUnit.SECONDS);
        //最好不要直接return success，因为我们返回的是boolean类型，现在得到的是Boolean的结果，就会进行自动装箱，如果success为null,就会出现空指针异常
        return Boolean.TRUE.equals(success);//null的话也是返回false
    }

//    @Override
//    public void unlock() {
//        String key = KEY_PREFIX + name;
//        //获取线程标识
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        //获取锁中的标识
//        if (stringRedisTemplate.opsForValue().get(key).equals(threadId)) {
//            stringRedisTemplate.delete(key);
//        }
//    }


    public void unlock(){
        //调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId()
        );
    }


}
