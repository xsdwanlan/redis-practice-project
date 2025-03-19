//package com.hmdp;
//
//import lombok.extern.slf4j.Slf4j;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.redisson.api.RLock;
//import org.redisson.api.RedissonClient;
//import org.springframework.boot.test.context.SpringBootTest;
//
//import javax.annotation.Resource;
//
///**
// * @wwj
// * @date
// */
//
//@Slf4j
////@SpringBootTest
//public class RedissonTest {
////
////    @Resource(name = "redissonClient")
////    private RedissonClient redissonClient;
////
////    @Resource(name = "redissonClient2")
////    private RedissonClient redissonClient2;
////
////    @Resource(name = "redissonClient3")
////    private RedissonClient redissonClient3;
////    private RLock lock;
//
//
//
//
//    //可重入锁测试——redisson
//    //主从一致测试，联锁
//    //注意，这里的联锁测试要有redis运行在后两个端口上，要不然没有实例
//
//
//
//
//
//
//
//
////    @BeforeEach
////        // 创建 Lock 实例（可重入）
////    void setUp() {
////        lock = redissonClient.getLock("order");
////    }
////
////    @BeforeEach
////    void setUp() {
////        RLock lock1 = redissonClient.getLock("order");
////        RLock lock2 = redissonClient2.getLock("order");
////        RLock lock3 = redissonClient3.getLock("order");
////        //创建连锁Lock
////        lock = redissonClient.getMultiLock(lock1,lock2,lock3);
////           }
////
////
////
////    @Test
////    void method1() throws InterruptedException {
////        boolean isLocked = lock.tryLock();
////        log.info(lock.getName());
////        if (!isLocked) {
////            log.error("Fail To Get Lock~1");
////            return;
////        }
////        try {
////            log.info("Get Lock Successfully~1");
////            method2();
////        } finally {
////            log.info("Release Lock~1");
////            lock.unlock();
////        }
////    }
////
////    @Test
////    void method2() throws InterruptedException {
////        boolean isLocked = lock.tryLock();
////        if (!isLocked) {
////            log.error("Fail To Get Lock!~2");
////            return;
////        }
////        try {
////            log.info("Get Lock Successfully!~2");
////        } finally {
////            log.info("Release Lock!~2");
////            lock.unlock();
////        }
////    }
//}