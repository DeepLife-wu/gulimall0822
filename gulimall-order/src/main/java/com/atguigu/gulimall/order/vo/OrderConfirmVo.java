package com.atguigu.gulimall.order.vo;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;


@Data
public class OrderConfirmVo {
    //收货地址，ums_member_receive_address表
    List<MemberAddressVo> address;
    //所有选中的购物项
    List<OrderItemVo> items;
    //发票...

    //优惠卷信息...
    Integer integration;
    //定单总额
    BigDecimal total;
    //应付价格
    BigDecimal payPrice;

    public Integer getCount() {
        Integer i = 0;
        if(CollectionUtils.isNotEmpty(items)) {
            for (OrderItemVo item : items) {
                i += item.getCount();
            }
        }
        return i;
    }

    public BigDecimal getPayPrice() {
        return getTotal();
    }

    public BigDecimal getTotal() {
        BigDecimal sum = new BigDecimal("0");
        if(CollectionUtils.isNotEmpty(items)) {
            for (OrderItemVo item : items) {
                BigDecimal result = item.getPrice().multiply(new BigDecimal(item.getCount().toString()));
                sum = sum.add(result);
            }
        }
        return sum;
    }

    //订单防重提交令牌
    String orderToken;

    Map<Long,Boolean > stocks;

}
