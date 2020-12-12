package com.atguigu.gulimall.seckill.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.seckill.feign.CouponFeignService;
import com.atguigu.gulimall.seckill.feign.ProductFeignService;
import com.atguigu.gulimall.seckill.to.SecKillSkuRedisTo;
import com.atguigu.gulimall.seckill.vo.SeckillSessionsWithSkusVo;
import com.atguigu.gulimall.seckill.vo.SeckillSkuVo;
import com.atguigu.gulimall.seckill.vo.SkuInfoVo;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;
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
            List<String> collect = session.getRelationSkus().stream().map(item -> item.getSkuId().toString()).collect(Collectors.toList());
            //缓存活动信息
            redisTemplate.opsForList().leftPushAll(key,collect);
        });
    }

    private void saveSessionSkuInfos(List<SeckillSessionsWithSkusVo> sessions) {
        sessions.stream().forEach(session->{
            //准备hash操作
            BoundHashOperations<String, Object, Object> ops = redisTemplate.boundHashOps(SKUKILL_CACHE_PREFIX);

            session.getRelationSkus().stream().forEach(seckillSkuVo->{
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
                String token = UUID.randomUUID().toString().replace("-", "");
                redisTo.setRandomCode(token);

                //5。使用分布式的信号量 限流
                RSemaphore semaphore = redissonClient.getSemaphore(SKU_STOCK_SEMAPHORE + token);
                //库存有多少，信号有多少
                semaphore.trySetPermits(seckillSkuVo.getSeckillCount().intValue());

                String s = JSON.toJSONString(redisTo);
                ops.put(seckillSkuVo.getId().toString(),s);
            });
        });
    }

}
