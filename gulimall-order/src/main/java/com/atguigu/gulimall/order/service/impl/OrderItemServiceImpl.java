package com.atguigu.gulimall.order.service.impl;

import com.atguigu.gulimall.order.entity.OrderEntity;
import com.atguigu.gulimall.order.entity.OrderReturnReasonEntity;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.order.dao.OrderItemDao;
import com.atguigu.gulimall.order.entity.OrderItemEntity;
import com.atguigu.gulimall.order.service.OrderItemService;

@RabbitListener(queues = {"hello-java-queue"})
@Service("orderItemService")
public class OrderItemServiceImpl extends ServiceImpl<OrderItemDao, OrderItemEntity> implements OrderItemService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<OrderItemEntity> page = this.page(
                new Query<OrderItemEntity>().getPage(params),
                new QueryWrapper<OrderItemEntity>()
        );

        return new PageUtils(page);
    }

//    @RabbitListener(queues = {"hello-java-queue"})
    @RabbitHandler
    public void receiveMessage(Message message, OrderReturnReasonEntity content,
                               Channel channel) throws InterruptedException, IOException {
        byte[] body = message.getBody();
        MessageProperties properties = message.getMessageProperties();
//        System.out.println("接收到的消息...内容..." + message + " ===> " + message.getClass());
        System.out.println("接收到的消息..." + content);
//        Thread.sleep(3000);
        System.out.println("消息处理完成=>" + content.getName());
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        System.out.println("deliveryTag==>" + deliveryTag);
        //签收货物，非批量
        try {
            if(deliveryTag % 2 == 0) {
                channel.basicAck(deliveryTag,false);
                System.out.println("签收了货物..." + deliveryTag);
            } else {
                channel.basicNack(deliveryTag,false,true);
                System.out.println("没有签收了货物..." + deliveryTag);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @RabbitHandler
    public void receiveMessage2(OrderEntity content ) throws InterruptedException {
        System.out.println("接收到的消息..." + content);
//        Thread.sleep(3000);
    }

}