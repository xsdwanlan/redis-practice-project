package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private IVoucherOrderService proxy;

    //秒杀lua脚本
    private static final DefaultRedisScript<Long> SECKI_SCRIPT;
    static{//写成静态代码块，类加载就可以完成初始定义，就不用每次释放锁都去加载这个，性能提高咯
        SECKI_SCRIPT = new DefaultRedisScript<>();
        SECKI_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));//设置脚本位置
        SECKI_SCRIPT.setResultType(Long.class);
    }
    //阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);//创建阻塞队列
    //线程池，通过一个单线程处理
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();//创建线程池

    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始，是否结束
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始!");
        }
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已结束!");
        }
        //3.判断库存是否充足
        if(voucher.getStock()<=0){
            return Result.fail("优惠券库存不足!");
        }
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //生成订单id
        long orderId = redisIdWorker.nextId("order");
        //1.执行Lua脚本
        Long result = stringRedisTemplate.execute(
                SECKI_SCRIPT,
                Collections.emptyList(),//空List
                voucherId.toString(), userId.toString(),String.valueOf(orderId)
        );
        //2.判断结果是否0 是0就是成功，可下单，下单信息保存到阻塞队列
        if(result!=0){
            return Result.fail(result==1?"库存不足!":"不能重复下单!");
        }
        //获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();//获得代理对象
        //3.返回订单id
        return Result.ok(orderId);

    }























    //阻塞队列实现版本
//    // 判断库存和进行一人一单判断后将信息放入java阻塞队列
//    public Result seckillVoucher(Long voucherId) {
//        //1.查询优惠卷
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        //2.判断秒杀是否开始，是否结束
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始!");
//        }
//        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
//            return Result.fail("秒杀已结束!");
//        }
//        //3.判断库存是否充足
//        if(voucher.getStock()<=0){
//            return Result.fail("优惠券库存不足!");
//        }
//        //获取当前用户
//        Long userId = UserHolder.getUser().getId();
//        //1.执行Lua脚本
//        Long result = stringRedisTemplate.execute(
//                SECKI_SCRIPT,
//                Collections.emptyList(),//空List
//                voucherId.toString(), userId.toString()
//        );
//        //2.判断结果是否0 是0就是成功，可下单，下单信息保存到阻塞队列
//        if(result!=0){
//            return Result.fail(result==1?"库存不足!":"不能重复下单!");
//        }
//        //生成订单id
//        long orderId = redisIdWorker.nextId("order");
//        //创建订单数据
//        VoucherOrder voucherOrder = new VoucherOrder();
//        voucherOrder.setUserId(userId);
//        voucherOrder.setId(orderId);
//        voucherOrder.setVoucherId(voucherId);
//        //放入阻塞队列
//        orderTasks.add(voucherOrder);
//        //获取代理对象
//        proxy = (IVoucherOrderService) AopContext.currentProxy();//获得代理对象
//        //3.返回订单id
//        return Result.ok(orderId);
//
//    }

    // （类加载后就执行）持续从阻塞队列出取出订单信息
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

   private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
            //下面的代码需要redis 版本在5以上，才能使用stream流，24年12.4写的时候用的是3.12版本
//            while(true){
//                try {
//                    //1.获取消息队列中的消息 xreadgroup group g1 c1 count 1 block 2000 streams stream.orders >
//                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
//                            Consumer.from("g1", "c1"),
//                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
//                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
//                    );
//                    //2.判断获取是否成功
//                    //3.失败就再循环
//                    if(list==null||list.isEmpty()){
//                        continue;
//                    }
//                    //4.成功就创建订单且ACK确认
//                    //解析消息
//                    MapRecord<String, Object, Object> record = list.get(0);//消息id,key,value
//                    //取出每个消息
//                    Map<Object, Object> values = record.getValue();
//                    //转为VoucherOrder实体类
//                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
//                    //创建订单
//                    handleVoucherOrder(voucherOrder);
//                    //ACK确认
//                    stringRedisTemplate.opsForStream().acknowledge("stream.orders","g1",record.getId());
//                } catch (Exception e) {
//                    log.error("处理订单异常:",e);
//                    //5.出现异常，从pendingList中取出数据后重新操作
//                    handlePendingList();
//                }
//            }
         }
   }

    private void handlePendingList() {
        while (true) {
            try {
                // 1. 获取 pending-list 中的订单信息
                // XREAD GROUP orderGroup consumerOne COUNT 1 STREAM stream.orders 0
                List<MapRecord<String, Object, Object>> readingList = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create("stream.orders", ReadOffset.from("0"))
                );

                // 2. 判断消息是否获取成功
                if (readingList.isEmpty() || readingList == null) {
                    // 获取失败 pending-list 中没有异常消息，结束循环
                    break;
                }

                // 3. 解析消息中的订单信息并下单
                MapRecord<String, Object, Object> record = readingList.get(0);
                Map<Object, Object> recordValue = record.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(recordValue, new VoucherOrder(), true);
                handleVoucherOrder(voucherOrder);

                // 4. XACK
                stringRedisTemplate.opsForStream().acknowledge("stream.orders", "g1", record.getId());
            } catch (Exception e) {
                log.error("订单处理异常（pending-list）", e);
                try {
                    // 稍微休眠一下再进行循环
                    Thread.sleep(10);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }


    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();//由于多线程，所以不能直接去ThreadLocal取
        //创建锁对象
        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId,stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁
        boolean hasLock = lock.tryLock( );
        if(!hasLock){
            //获取锁失败
            log.error("不允许重复下单!");
            return;
        }

        try {
            //代理对象改成全局变量
            proxy.createVoucherOrder(voucherOrder);//默认是this,我们要实现事务需要proxy
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }

    }


















    //线程池中的线程的线程任务——java阻塞队列版本
//    private class VoucherOrderHandler implements Runnable{
//
//        @Override
//        public void run() {
//            while(true){
//                try {
//                    //1.获取订单中的队列消息
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    handleVoucherOrder(voucherOrder);
//                    //2.创建订单
//                } catch (Exception e) {
//                    log.error("处理订单异常:",e);
//                }
//            }
//        }
//    }
//
//    //异步下单
//    private void handleVoucherOrder(VoucherOrder voucherOrder) {
//        Long userId = voucherOrder.getUserId();//由于多线程，所以不能直接去ThreadLocal取
//        //创建锁对象
//        //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId,stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        //获取锁
//        boolean hasLock = lock.tryLock( );
//        if(!hasLock){
//            //获取锁失败
//            log.error("不允许重复下单!");
//            return;
//        }
//
//        try {
//            //代理对象改成全局变量
//            proxy.createVoucherOrder(voucherOrder);//默认是this,我们要实现事务需要proxy
//        } catch (IllegalStateException e) {
//            throw new RuntimeException(e);
//        } finally {
//            lock.unlock();
//        }
//
//    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder){
        //查询订单看看是否存在
        Long userId = UserHolder.getUser().getId();

        if (query().eq("user_id",userId).eq("voucher_id", voucherOrder.getUserId()).count()>0) {
            log.error("用户已经购买过一次!");
            return;
        }

        //4.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock",0)//where id = ? and stock >0 添加了乐观锁
                .update();

        if(!success){
            log.error("优惠券库存不足!");
            return;
        }

        //7.订单写入数据库
        save(voucherOrder);
    }





//  基于redisson等锁方法完成一人一单
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //1.查询优惠卷
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        //2.判断秒杀是否开始，是否结束
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始!");
//        }
//        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
//            return Result.fail("秒杀已结束!");
//        }
//        //3.判断库存是否充足
//        if(voucher.getStock()<=0){
//            return Result.fail("优惠券库存不足!");
//        }
//
//        Long userId = UserHolder.getUser().getId();
//
//
//
//            //使用JDK自带的锁
////        synchronized (userId.toString().intern()) {//userId一样的持有同一把锁，最好不要放在整个方法上,intern:去字符串常量池找相同字符串
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();//获得代理对象
////            return proxy.createVoucherOrder(voucherId);//默认是this,我们要实现事务需要proxy
////        }//先获取锁，然后再进入方法，确保我的前一个订单会添加上,能先提交事务再释放锁
//
//
////
////        //分布式锁——初级版本
////        //创建锁对象
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId,stringRedisTemplate);
////
//
//        //创建锁对象——Redisson Api调用
//       RLock lock = redissonClient.getLock("lock:order:" + userId);
//
//
//
//        //获取锁
//        boolean hasLock = lock.tryLock();
//        if(!hasLock){
//            //获取锁失败: return fail 或者 retry 这里业务要求是返回失败
//            return Result.fail("请勿重复下单!");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();//获得代理对象
//            return proxy.createVoucherOrder(voucherId);//默认是this,我们要实现事务需要proxy
//        } catch (IllegalStateException e) {
//            throw new RuntimeException(e);
//        } finally {
//            lock.unlock();
//        }
//    }






//    @Transactional
//    public Result createVoucherOrder(VoucherOrder voucherId){
//        //查询订单看看是否存在
//        Long userId = UserHolder.getUser().getId();
//
//        if (query().eq("user_id",userId).eq("voucher_id",voucherId).count()>0) {
//            return Result.fail("用户已经购买过一次!");
//        }
//
//        //4.扣减库存
//        boolean success = seckillVoucherService.update()
//                .setSql("stock = stock -1")
//                .eq("voucher_id", voucherId).gt("stock",0)//where id = ? and stock >0 添加了乐观锁
//                .update();
//        //5.创建订单
//        if(!success){
//            return Result.fail("优惠券库存不足!");
//        }
//
//        //6.返回订单id
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //6.1订单id
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        //6.2用户id
//        //Long userId = UserHolder.getUser().getId();
//        voucherOrder.setUserId(userId);
//        //6.3代金券id
//        voucherOrder.setVoucherId(voucherId);
//
//        //7.订单写入数据库
//        save(voucherOrder);
//        //8.返回订单Id
//        return Result.ok(orderId);
//    }


}
