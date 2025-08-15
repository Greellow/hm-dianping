-- 1. 参数列表
-- ARGV[1]: 优惠券id
local voucherId = ARGV[1]
-- ARGV[2]: 用户id
local userId = ARGV[2]

local orderId = ARGV[3]

-- 2. 数据Key的定义
-- 库存Key
local stockKey = 'seckill:stock:' .. voucherId
-- 订单Key (用Set集合判断一人一单)
local orderKey = 'seckill:order:' .. voucherId

-- 3. 脚本业务逻辑
-- 3.1. 判断库存是否充足
if(tonumber(redis.call('get', stockKey)) <= 0) then
    -- 库存不足，返回1
    return 1
end

-- 3.2. 判断用户是否已下单 (一人一单)
if(redis.call('sismember', orderKey, userId) == 1) then
    -- 已经下过单，是重复下单，返回2
    return 2
end

-- 3.3. 扣减库存
redis.call('incrby', stockKey, -1)
-- 3.4. 记录下单用户
redis.call('sadd', orderKey, userId)

redis.call('xadd','stream.orders','*','userId',userId,'voucherId',voucherId, 'id',orderId)
-- 抢购成功，返回0
return 0