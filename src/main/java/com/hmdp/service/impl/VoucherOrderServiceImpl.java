package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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

    @Override
    public Result seckillVoucher(Long voucherId) {
        //查询优惠券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        //判断是否在时间内 秒杀开始和秒杀结束
        if(seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀活动未开始");
        }
        if(seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已经结束");
        }

        //判断是否有库存

        if (seckillVoucher.getStock() <1){
            return Result.fail("库存不足");
        }

        //一人一单
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId, seckillVoucher);
        }
    }

    @Transactional
    public  Result createVoucherOrder(Long voucherId, SeckillVoucher seckillVoucher) {
        Long userId = UserHolder.getUser().getId();
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count > 0) {
                return Result.fail("已经购买过了");
            }

            boolean success = seckillVoucherService.update().
                    setSql("stock = stock - 1").
                    eq("voucher_id", voucherId).
                    eq("stock", seckillVoucher.getStock()).gt("stock", 0).
                    update();

            if (!success) {
                return Result.fail("库存不足");
            }

            //创建订单
            VoucherOrder order = new VoucherOrder();
            Long orderId = redisIdWorker.nextId("order");
            order.setId(orderId);

            order.setUserId(userId);
            order.setVoucherId(voucherId);

            return Result.ok(orderId);

    }
}
