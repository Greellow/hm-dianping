package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        if(StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }

        if(shopJson != null ){
            return Result.fail("店铺不存在");
        }

        Shop shop = getById(id);
        if(shop == null){
            stringRedisTemplate.opsForValue().set(key, "",2L, TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),30L, TimeUnit.MINUTES);

        return Result.ok(shop);
    }

    @Override
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("不存在");
        }
        //更新数据库
        updateById(shop);
        //删除缓存
        redisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
