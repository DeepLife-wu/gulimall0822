package com.atguigu.gulimall.ware.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class FeeVo {

    private MemberAddressVo address;
    private BigDecimal fee;

}
