package com.atguigu.gulimall.order.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FeeVo {
    private MemberAddressVo address;
    private BigDecimal fee;
}
