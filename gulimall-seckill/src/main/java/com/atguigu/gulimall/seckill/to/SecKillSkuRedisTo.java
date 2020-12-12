package com.atguigu.gulimall.seckill.to;

import com.atguigu.gulimall.seckill.vo.SkuInfoVo;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SecKillSkuRedisTo {
    private Long id;
    private Long promotionId;
    private Long promotionSessionId;
    private Long skuId;
    private BigDecimal seckillPrice;
    private BigDecimal seckillCount;
    private BigDecimal seckillLimit;
    private Integer seckillSort;

    //sku的详细信息
    private SkuInfoVo skuInfo;

    //当前商品秒杀开始结束时间
    private Long startTime;
    private Long endTime;

    //随机码
    private String randomCode;
}
