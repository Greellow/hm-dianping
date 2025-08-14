package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀优惠券下单
     * @param voucherId 优惠券id
     * @return 订单id
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 创建优惠券订单（异步）
     * @param voucherOrder 订单信息
     */
    void createVoucherOrder(VoucherOrder voucherOrder);
}