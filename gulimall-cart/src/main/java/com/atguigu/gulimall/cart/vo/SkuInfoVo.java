package com.atguigu.gulimall.cart.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SkuInfoVo {
    private Long skuId;
    /**
     * $column.comments
     */
    private Long spuId;
    /**
     * $column.comments
     */
    private String skuName;
    /**
     * $column.comments
     */
    private String skuDesc;
    /**
     * $column.comments
     */
    private Long catalogId;
    /**
     * $column.comments
     */
    private Long brandId;
    /**
     * $column.comments
     */
    private String skuDefaultImg;
    /**
     * $column.comments
     */
    private String skuTitle;
    /**
     * $column.comments
     */
    private String skuSubtitle;
    /**
     * $column.comments
     */
    private BigDecimal price;
    /**
     * $column.comments
     */
    private Long saleCount;
}
