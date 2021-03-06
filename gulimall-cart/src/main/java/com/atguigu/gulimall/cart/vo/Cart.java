package com.atguigu.gulimall.cart.vo;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 购物车*/
@Data
public class Cart {

    private List<CartItem> items;
    private Integer countNum;   //商品数量
    private Integer countType;  //商品类型数量
    private BigDecimal totalAmount; //商品总价
    private BigDecimal reduce = new BigDecimal("0.00");  //减免价格

    public Integer getCountNum() {
        int count = 0;
        if(CollectionUtils.isNotEmpty(items)) {
            for (CartItem item : items) {
                count += item.getCount();
            }
        }
        return count;
    }

    public Integer getCountType() {
        int count = 0;
        if(CollectionUtils.isNotEmpty(items)) {
            for (CartItem item : items) {
                count += 1;
            }
        }
        return count;
    }

    public BigDecimal getTotalAmount() {
        BigDecimal amount = new BigDecimal("0");
        //1.计算购物项总价
        if(CollectionUtils.isNotEmpty(items)) {
            for (CartItem item : items) {
                if(item.getCheck()) {
                    BigDecimal totalPrice = item.getTotalPrice();
                    amount = amount.add(totalPrice);
                }
            }
        }
        //2.减去优惠总价
        BigDecimal subtract = amount.subtract(getReduce());
        return subtract;
    }

    public BigDecimal getReduce() {
        return reduce;
    }
}
