package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @wwj
 * @date
 */
@Component
@Slf4j
public class CacheClient {
    private StringRedisTemplate stringRedisTemplate;


    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        // 设置逻辑过期
        RedisData redisData = new RedisData();

        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**解决缓存穿透**/
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack, Long time, TimeUnit unit){

        String key = keyPrefix + id;
        //1.从Redis内查询商品缓存
        String json = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if(StrUtil.isNotBlank(json)){
            //手动反序列化
            return JSONUtil.toBean(json, type);
        }
        //如果上面的判断不对，那么就是我们设置的""(有缓存"",证明数据库内肯定是没有的)或者null(没有缓存)
        //判断命中的是否时空值
        if(json!=null){//
            return null;
        }
        //2.不存在就根据id查询数据库
        R r = dbFallBack.apply(id);//由于不知道这段逻辑，所以我们需要用户传进来函数逻辑
        if(r==null){
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //写入Redis
        this.set(key,r,time,unit);
        return r;
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);//10个线程的线程池
    /**逻辑过期实现解决缓存击穿**/
    public <R,ID> R queryWithLogical(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack,Long time,TimeUnit unit) {
        String key = CACHE_SHOP_KEY + id;
        //1.从Redis内查询商品缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        //3.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);

        //4.判断是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期直接返回
            return r;
        }
        //5.过期的话需要缓存重建
        //5.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean hasLock = tryLock(lockKey);
        //5.2判断是否获取到，获取到:根据id查数据库 获取不到:休眠
        if (hasLock) {
            //成功就开启独立线程，实现缓存重建, 这里的话用线程池
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存(查数据库+传入Redis)
                    R r1 = dbFallBack.apply(id);
                    this.setWithLogicalExpire(key,r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        return r;
    }

    //设置锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);//如果存在
        return BooleanUtil.isTrue(flag);

    }
    //修改锁
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }


}
