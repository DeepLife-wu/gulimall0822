package com.atguigu.gulimall.seckill.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.to.mq.SeckillOrderTo;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.MemberResponseVo;
import com.atguigu.gulimall.seckill.feign.CouponFeignService;
import com.atguigu.gulimall.seckill.feign.ProductFeignService;
import com.atguigu.gulimall.seckill.interceptor.LoginUserInterceptor;
import com.atguigu.gulimall.seckill.to.SecKillSkuRedisTo;
import com.atguigu.gulimall.seckill.vo.SeckillSessionsWithSkusVo;
import com.atguigu.gulimall.seckill.vo.SeckillSkuVo;
import com.atguigu.gulimall.seckill.vo.SkuInfoVo;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SeckillService {

    private static final String SESSIONS_CACHE_PREFIX = "seckill:sessions:";
    private static final String SKUKILL_CACHE_PREFIX = "seckill:skus:";
    private static final String SKU_STOCK_SEMAPHORE = "seckill:stock:";

    @Autowired
    private CouponFeignService couponFeignService;
    @Autowired
    private ProductFeignService productFeignService;

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void uploadSeckillSkuLatest3Days() {
        //1.扫描最近3天需要参与秒杀的活动
        R session = couponFeignService.getLates3DaySession();
        if(session.getCode() == 0) {
            //上架商品
            List<SeckillSessionsWithSkusVo> sessionData = session.getData(new TypeReference<List<SeckillSessionsWithSkusVo>>() {
            });
            //缓存到redis
            //1。缓存活动信息
            saveSessionInfos(sessionData);
            //2。缓存活动的关联商品信息
            saveSessionSkuInfos(sessionData);
        }
    }

    private void saveSessionInfos(List<SeckillSessionsWithSkusVo> sessions) {
        sessions.stream().forEach(session -> {
            Long startTime = session.getStartTime().getTime();
            Long endTime = session.getEndTime().getTime();
            String key = SESSIONS_CACHE_PREFIX + startTime + "_" + endTime;
            List<String> collect = session.getRelationSkus().stream().map(item -> item.getPromotionSessionId() + "_" + item.getSkuId().toString()).collect(Collectors.toList());

            Boolean hasKey = redisTemplate.hasKey(key);
            if(!hasKey) {
                //缓存活动信息
                redisTemplate.opsForList().leftPushAll(key,collect);
            }
        });
    }

    private void saveSessionSkuInfos(List<SeckillSessionsWithSkusVo> sessions) {
        sessions.stream().forEach(session->{
            //准备hash操作
            BoundHashOperations<String, Object, Object> ops = redisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);

            session.getRelationSkus().stream().forEach(seckillSkuVo->{
                String token = UUID.randomUUID().toString().replace("-", "");

                if(!ops.hasKey(
                        seckillSkuVo.getPromotionSessionId() + "_" +
                        seckillSkuVo.getSkuId().toString()
                )) {
                    //缓存商品
                    SecKillSkuRedisTo redisTo = new SecKillSkuRedisTo();
                    //1。sku的基本数据
                    R skuInfo = productFeignService.getSkuInfo(seckillSkuVo.getSkuId());
                    if(skuInfo.getCode() == 0) {
                        SkuInfoVo info = skuInfo.getData("skuInfo", new TypeReference<SkuInfoVo>() {
                        });
                        redisTo.setSkuInfo(info);
                    }
                    //2。sku的秒杀信息
                    BeanUtils.copyProperties(seckillSkuVo,redisTo);
                    //3。设置上当前商品的秒杀时间信息
                    redisTo.setStartTime(session.getStartTime().getTime());
                    redisTo.setEndTime(session.getEndTime().getTime());

                    //4。商品随机码
                    redisTo.setRandomCode(token);
                    String s = JSON.toJSONString(redisTo);
                    ops.put(seckillSkuVo.getPromotionSessionId() + "_"+ seckillSkuVo.getSkuId().toString(),s);

                    //5。使用分布式的信号量 限流
                    RSemaphore semaphore = redissonClient.getSemaphore(SKU_STOCK_SEMAPHORE + token);
                    //库存有多少，信号有多少
                    semaphore.trySetPermits(seckillSkuVo.getSeckillCount().intValue());
                }

            });
        });
    }

    public List<SecKillSkuRedisTo> getCurrentSeckillSkus() {
        //1。确定当前时间属于哪个场次
        long time = new Date().getTime();
        Set<String> keys = redisTemplate.keys(SESSIONS_CACHE_PREFIX + "*");
        for (String key : keys) {
            String replace = key.replace(SESSIONS_CACHE_PREFIX, "");
            String[] s = replace.split("_");
            Long start = Long.parseLong(s[0]);
            Long end = Long.parseLong(s[1]);
            if(time >= start && time <= end) {
                //2。获取这个秒杀场次需要的所有商品信息
                List<String> range = redisTemplate.opsForList().range(key, -100, 100);
                BoundHashOperations<String, String, Object> hashOps = redisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
                List<Object> list = hashOps.multiGet(range);
                if(CollectionUtils.isNotEmpty(list)) {
                    List<SecKillSkuRedisTo> collect = list.stream().map(item -> {
                        SecKillSkuRedisTo redisTo = JSON.parseObject(item.toString(), SecKillSkuRedisTo.class);
//                        redisTo.setRandomCode(""); //当前秒杀开始需要随机码
                        return redisTo;
                    }).collect(Collectors.toList());
                    return collect;
                }
                break;
            }
        }

        return null;
    }

    public SecKillSkuRedisTo getSkuSeckillInfo(Long skuId) {
        //1.找到所有需要参与秒杀的商品key
        BoundHashOperations<String, String, String> hashOps = redisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
        Set<String> keys = hashOps.keys();
        if(CollectionUtils.isNotEmpty(keys)) {
            String regx = "\\d_" + skuId;
            for (String key : keys) {
                if(Pattern.matches(regx,key)) {
                    String json = hashOps.get(key);
                    SecKillSkuRedisTo skuRedisTo = JSON.parseObject(json, SecKillSkuRedisTo.class);
                    //随机码
                    long current = new Date().getTime();
                    if(current >= skuRedisTo.getStartTime() && current <= skuRedisTo.getEndTime()) {
                    } else {
                        skuRedisTo.setRandomCode("");
                    }

                    return skuRedisTo;
                }
            }
        }
        return null;
    }

    public String kill(String killId, String key, Integer num) {
        MemberResponseVo memberResponseVo = LoginUserInterceptor.loginUser.get();
        //1.获取当前秒杀商品的详细信息
        BoundHashOperations<String, String, String> hashOps = redisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);
        String json = hashOps.get(killId);
        if(StringUtils.isBlank(json)) {
            return null;
        } else {
            SecKillSkuRedisTo redis = JSON.parseObject(json, SecKillSkuRedisTo.class);
            //校验合法性
            Long startTime = redis.getStartTime();
            Long endTime = redis.getEndTime();
            Long time = new Date().getTime();
            if(time >= startTime && time <= endTime) {
                //2.校验随机码和商品id
                String randomCode = redis.getRandomCode();
                String skuId = redis.getPromotionSessionId() + "_" + redis.getSkuId();
                if(randomCode.equals(key) && killId.equals(skuId)) {
                    //3.验证购物的数量是否合理
                    if(num <= redis.getSeckillLimit().intValue()) {
                        //4.验证这个人是否已经买过了
                        String redisKey = memberResponseVo.getId() + "_" + skuId;
                        //自动过期
                        Long ttl = endTime - startTime;
                        Boolean aBoolean = redisTemplate.opsForValue().setIfAbsent(redisKey, num.toString(), ttl, TimeUnit.MILLISECONDS);
                        if(aBoolean) {
                            //占位成功，说明此人从来没有买过
                            RSemaphore semaphore = redissonClient.getSemaphore(SKU_STOCK_SEMAPHORE + randomCode);
                            try {
                                boolean b = semaphore.tryAcquire(num, 100, TimeUnit.MILLISECONDS);
                                if(b) {
                                    //秒杀成功
                                    //快速下单,发送mq消息
                                    String timeId = IdWorker.getTimeId();
                                    SeckillOrderTo orderTo = new SeckillOrderTo();
                                    orderTo.setOrderSn(timeId);
                                    orderTo.setMemberId(memberResponseVo.getId());
                                    orderTo.setNum(num);
                                    orderTo.setPromotionSessionId(redis.getPromotionSessionId());
                                    orderTo.setSkuId(redis.getSkuId());
                                    orderTo.setSeckillPrice(redis.getSeckillPrice());
                                    rabbitTemplate.convertAndSend("order-event-exchange","order.seckill.order",orderTo);
                                    return timeId;
                                }
                            } catch (InterruptedException e) {
                                return null;
                            }
                        } else {
                            return null;
                        }
                    }
                } else{
                    return null;
                }
            } else {
                return null;
            }
        }

        return null;
    }

}












