package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private ShopServiceImpl shopService;
    @Autowired
    private CacheClient cacheClient;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


//    @Test
//    void testSaveShop1() throws InterruptedException {
//        shopService.saveShop2Redis(1L,10L);
//    }
//    @Test
//    void testSaveShop() throws InterruptedException {
//        Shop shop = shopService.getById(1L);
//        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L,shop,1L, TimeUnit.SECONDS);
//    }
//@Resource
//private RedisIdWorker redisIdWorker;
//
//    private ExecutorService es = Executors.newFixedThreadPool(500);//线程池
//
//    @Test
//    void testRedisId() throws InterruptedException {
//        //CountDownLatch大致的原理是将任务切分为N个，让N个子线程执行，并且有一个计数器也设置为N，哪个子线程完成了就N-1
//        CountDownLatch latch = new CountDownLatch(300);
//
//        Runnable task =()->{
//            for(int i=0;i<100;i++){
//                Long id = redisIdWorker.nextId("order");
//                System.out.println("id = " + id);
//            }
//            latch.countDown();
//        };
//        Long begin = System.currentTimeMillis();
//        for(int i=0;i<300;i++){
//            es.submit(task);
//        }
//        latch.await();
//        Long end = System.currentTimeMillis();
//        System.out.println("time = " + (end - begin));
//    }

//    @Test
    public void localshopData(){
        //1.查询店铺信息
        List<Shop> shops = shopService.list();
        //2.店铺按照typeId进行分组 map<typeId,店铺集合>
        //Map<Long,List<Shop>> map = shops.stream().collect(Collectors.groupingBy(shop -> shop.getTypeId()));
        Map<Long,List<Shop>> map = shops.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3.分批写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            //3.1获取类型id
            Long typeId = entry.getKey();
            String typeKey = SHOP_GEO_KEY + typeId;
            //3.2获取这个类型的所有店铺，组成集合
            List<Shop> value = entry.getValue();
            //3.3 写入Redis geoadd key 经度 维度 member
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            for (Shop shop : value) {
                //stringRedisTemplate.opsForZSet().add(typeKey, new Point(shop.getX(),shop.getY()), shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(),shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(typeKey,locations);
        }
    }
















}
