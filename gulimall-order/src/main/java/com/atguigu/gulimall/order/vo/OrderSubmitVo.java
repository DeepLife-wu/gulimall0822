package com.atguigu.gulimall.order.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderSubmitVo {
    //收货地址的id
    private Long addrId;
    private Integer payType;
    //无须提交购买的商品，去购物车取一次
    //优惠，发票

    //防重令牌
    private String orderToken;
    //应付价格
    private BigDecimal payPrice;

    private String note;//订单备注

    //用户相关信息在session里取

}
