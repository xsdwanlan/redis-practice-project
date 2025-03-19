package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;



    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop = queryWithPassThrough(id);

        //互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);
//        if(shop==null) return Result.fail("店铺不存在!");
//        //逻辑过期解决缓存击穿
//        Shop shop = queryWithLogical(id);
//        if(shop==null) return Result.fail("店铺不存在!");


        //封装了工具类之后的写法
        Shop shop = cacheClient
                .queryWithLogical(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);
        if(shop==null) return Result.fail("店铺不存在!");






        return Result.ok(shop);
    }


    /**
     * 获取互斥锁
     */
//    private boolean tryLock(String key) {
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", TTL_TEN, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    /**
//     * 释放互斥锁
//     */
//    private void unlock(String key) {
//        stringRedisTemplate.delete(key);
//    }
//
//
//    /**互斥锁实现解决缓存击穿**/
//    public Shop queryWithMutex(Long id){
//        //1.从Redis内查询商品缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        if(StrUtil.isNotBlank(shopJson)){
//            //手动反序列化
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        //如果上面的判断不对，那么就是我们设置的""(有缓存"",证明数据库内肯定是没有的)或者null(没有缓存)
//        //判断命中的是否时空值
//        if(shopJson!=null){//
//            return null;
//        }
//
//        //a.实现缓存重建
//        //a.1 获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        Shop shop = null;
//        try {
//            boolean hasLock = tryLock(lockKey);
//            //a.2 判断是否获取到，获取到:根据id查数据库 获取不到:休眠
//            if(!hasLock){
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//
//            //2.不存在就根据id查询数据库
//            shop = getById(id);
//            //模拟重建的延时
//            Thread.sleep(200);
//            if(shop==null){
//                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//                return null;
//            }
//            //3.数据库数据写入Redis
//            //手动序列化
//            String shopStr = JSONUtil.toJsonStr(shop);
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,shopStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            //释放互斥锁
//            unlock(lockKey);
//        }
//
//        return shop;
//    }
//
//    //解决了缓存穿透
//    public Result queryWithPassThrough(Long id) {
//        //1.从Redis内查询商品缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        if(StrUtil.isNotBlank(shopJson)){
//            //手动反序列化
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return Result.ok(shop);
//        }
//        //判断命中的是不是null
//        if (shopJson!=null){
//            return Result.fail("店铺不存在！");
//        }
//        //2.不存在就根据id查询数据库
//        Shop shop = getById(id);
//        if(shop==null){
//            //解决缓存穿透，将空值写入redis
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return Result.fail("商户不存在！");
//        }
//        //3.数据库数据写入Redis
//        //手动序列化
//        String shopStr = JSONUtil.toJsonStr(shop);
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,shopStr,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        return Result.ok(shop);
//    }
//
//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);//开启10个线程
//    /**逻辑过期实现解决缓存击穿**/
//    public Shop queryWithLogical(Long id){
//        //1.从Redis内查询商品缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        //2.判断是否存在
//        if(StrUtil.isBlank(shopJson)){
//            return null;
//        }
//        //3.命中，需要先把json反序列化为对象
//        System.out.println("命中");
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        JSONObject data = (JSONObject) redisData.getData();
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//
//        //4.判断是否过期
//        LocalDateTime expireTime = redisData.getExpireTime();
//        if(expireTime.isAfter(LocalDateTime.now())){
//            //未过期直接返回
//            return shop;
//        }
//        //5.过期的话需要缓存重建
//        //5.1 获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean hasLock = tryLock(lockKey);
//        //5.2判断是否获取到，获取到:根据id查数据库 获取不到:休眠
//        if(hasLock){
//            //成功就开启独立线程，实现缓存重建, 这里的话用线程池
//            CACHE_REBUILD_EXECUTOR.submit(()->{
//                try {
//                    //重建缓存
//                    System.out.println("开启独立线程重建缓存");
//                    this.saveShop2Redis(id,20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }finally {
//                    //释放锁
//                    unlock(lockKey);
//                }
//
//            });
//
//        }
//
//        return shop;
//    }
//
//
//    //基于逻辑过期处理缓存击穿
//    /**缓存重建方法**/
//    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
//        //1.查询店铺信息
//        Shop shop = getById(id);
//        Thread.sleep(200);
//        //2.封装逻辑过期时间
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        //3.写入Redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
//    }
//














    @Override
    @Transactional
    public Result update(Shop shop) {
        if(shop.getId()==null){
            return Result.fail("店铺id不能为空!");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        String key = CACHE_SHOP_KEY + shop.getId();
        stringRedisTemplate.delete(key);
        return Result.ok();
    }



    //附近商铺
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //1.判断是否需要根据坐标进行查询
        if(x==null||y==null){
            Page<Shop> page = query().eq("type_id", typeId).page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        //2.计算分页参数
        int from = (current-1)*SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
//        //3.查询redis,按照距离排序，分页  geosearch bylonlat x y byredius 10 (km/m) withdistance
//        String typeKey = SHOP_GEO_KEY + typeId;
//        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
//                .search(
//                        typeKey,
//                        GeoReference.fromCoordinate(x, y),
//                        new Distance(5000),
//                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)//只能从0到end,后面需要自己截取
//                );
        //3.查询redis,按照距离排序，分页  geosearch bylonlat x y byredius 10 (km/m) withdistance
        String typeKey = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .radius(
                        typeKey,
                        new Circle(new Point(x, y), new Distance(5000, Metrics.KILOMETERS)),
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                .includeDistance()  // 包含距离
                                .sortAscending()    // 按距离升序排序
                                .limit(end)         // 限制返回的结果数量
                );
        //4.解析出id
        if(results==null){
            return Result.ok(Collections.emptyList());
        }
        //4.1我们要的地方的list集合(店铺Id+distance)
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        //有可能等下skip把数据都跳过了，所以需要判空
        if(content.size()<=from){
            //没有下一页
            return Result.ok(Collections.emptyList());
        }
        //4.2.截取first-end
        List<Long> ids = new ArrayList<>(content.size());
        Map<String, Distance> distMap = new HashMap<>(content.size());
        content.stream().skip(from).forEach(result ->{
            //4.2.1获取店铺Id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            //4.2.2获取距离
            Distance distance = result.getDistance();

            distMap.put(shopIdStr,distance);
        });//这里其实就是通过stream流将shopId提取出来，并且要根据距离进行排序

        //5.根据id查询shop
        String idStr = StrUtil.join(",",ids);//1,2,3,4...
        // .... where id in #{ids} order by field(id,1,2,3,4...) 根据id排序
        List<Shop> shops = query().in("id", ids).last("order by field(id," + idStr + ")").list();

        for (Shop shop : shops) {
            shop.setDistance(distMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }










}
