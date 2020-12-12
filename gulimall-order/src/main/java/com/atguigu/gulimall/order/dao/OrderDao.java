package com.atguigu.gulimall.order.dao;

import com.atguigu.gulimall.order.entity.OrderEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 订单
 * 
 * @author wuchao
 * @email 15801630979@163.com
 * @date 2020-08-23 19:53:26
 */
@Mapper
public interface OrderDao extends BaseMapper<OrderEntity> {

    void updateOrderPayedStatus(@Param("outTradeNo") String outTradeNo, @Param("code") Integer code);
}
