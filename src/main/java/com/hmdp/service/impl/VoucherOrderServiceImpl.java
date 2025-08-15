package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
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

    // Lua脚本的静态初始化
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // [警告修正] 阻塞队列只会初始化一次，可以声明为final
    //private final BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    // 异步下单的单线程执行器
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    private IVoucherOrderService proxy;

    // 在类初始化后就启动异步下单的线程
    @PostConstruct
    private void init() {
        // 创建stream和消费者组
        initStreamAndGroup();
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    /**
     * 初始化stream和消费者组
     */
    private void initStreamAndGroup() {
        String queueName = "streams.orders";
        try {
            // 尝试创建消费者组，使用MKSTREAM参数，如果stream不存在则创建stream
            stringRedisTemplate.opsForStream().createGroup(queueName, ReadOffset.from("0"), "g1");
        } catch (Exception e) {
            // 如果stream或group已存在，则忽略异常
            log.debug("Stream或消费者组已存在，无需重复创建", e);
        }
    }

    // [警告说明] 这是一个后台守护线程，无限循环是正常业务逻辑，IDE的警告可以忽略
    private class VoucherOrderHandler implements Runnable {
        String queueName = "streams.orders";
        @Override
        public void run() {
            while (true) {
                try {
                    // 从消息队列中获取订单任务，如果队列为空，线程会阻塞在这里
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );

                    //get usccess?

                    if(list == null || list.isEmpty()){
                        continue;
                    }
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(),true);
                    // [BUG修正] 调用下面的方法，处理订单
                    handleVoucherOrder(voucherOrder);

                     stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());


                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList(){
            while (true) {
                try {
                    // 从pendinglist中获取订单任务，如果队列为空，线程会阻塞在这里
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );

                    //get usccess?

                    if(list == null || list.isEmpty()){
                        break;
                    }
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(),true);
                    // [BUG修正] 调用下面的方法，处理订单
                    handleVoucherOrder(voucherOrder);

                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());


                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    // 真正处理数据库下单的逻辑
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // [BUG修正] 从voucherOrder中获取用户ID
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();

        if (!isLock) {
            log.error("不允许重复下单！");
            return;
        }
        try {
            // [BUG修正] 调用正确的createVoucherOrder方法
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");

        // 1. 执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();
        // 2. 判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 2.2. 为0，有购买资格，把下单信息放入阻塞队列
//        VoucherOrder voucherOrder = new VoucherOrder();
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
//        // 2.3.放入阻塞队列
//        orderTasks.add(voucherOrder);

        // 3. 获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        // 4.返回订单id
        return Result.ok(orderId);
    }

    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 一人一单最终校验
        Long userId = voucherOrder.getUserId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            log.error("用户已经购买过一次！");
            return;
        }

        // 扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足！");
            return;
        }
        // 创建订单
        save(voucherOrder);
    }
}