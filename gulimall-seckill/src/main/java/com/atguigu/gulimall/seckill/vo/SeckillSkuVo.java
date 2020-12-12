package com.atguigu.gulimall.seckill.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SeckillSkuVo {

    private Long id;
    private Long promotionId;
    private Long promotionSessionId;
    private Long skuId;
    private BigDecimal seckillPrice;
    private BigDecimal seckillCount;
    private BigDecimal seckillLimit;
    private Integer seckillSort;

}
