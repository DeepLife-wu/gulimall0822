package com.atguigu.gulimall.order;

import com.atguigu.gulimall.order.entity.OrderEntity;
import com.atguigu.gulimall.order.entity.OrderReturnReasonEntity;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;
import java.util.UUID;


@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class GulimallOrderApplicationTests {

    @Autowired
    private AmqpAdmin  amqpAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    public void sendMessageTest() {
//        rabbitTemplate.convertAndSend("hello-java-exchange","hello.java","Hello World!");
//        log.info("消息发送完成{}","Hello World!");

        /*OrderReturnReasonEntity reasonEntity = new OrderReturnReasonEntity();
        reasonEntity.setId(1L);
        reasonEntity.setCreateTime(new Date());
        reasonEntity.setName("哈哈");
        rabbitTemplate.convertAndSend("hello-java-exchange","hello.java",reasonEntity);
        log.info("消息发送完成{}",reasonEntity);*/

        /*for(int i = 0;i < 10; i++) {
            OrderReturnReasonEntity reasonEntity = new OrderReturnReasonEntity();
            reasonEntity.setId(1L);
            reasonEntity.setCreateTime(new Date());
            reasonEntity.setName("哈哈-" + i);
            rabbitTemplate.convertAndSend("hello-java-exchange","hello.java",reasonEntity);
            log.info("消息发送完成{}",reasonEntity);
        }*/

        for(int i = 0;i < 10; i++) {
            if(i % 2 == 0) {
                OrderReturnReasonEntity reasonEntity = new OrderReturnReasonEntity();
                reasonEntity.setId(1L);
                reasonEntity.setCreateTime(new Date());
                reasonEntity.setName("哈哈-" + i);
                rabbitTemplate.convertAndSend("hello-java-exchange","hello.java",reasonEntity);
//                log.info("消息发送完成{}",reasonEntity);
            } else {
                OrderEntity entity = new OrderEntity();
                entity.setOrderSn(UUID.randomUUID().toString());
                rabbitTemplate.convertAndSend("hello-java-exchange","hello.java",entity);
            }

        }
    }

    /**
     * 1.如何创建exchanage,queue,binding
     * 2.如何收发消息
     */
    @Test
    public void createExchange() {
        DirectExchange directExchange = new DirectExchange("hello-java-exchange",true,false);
        amqpAdmin.declareExchange(directExchange);
        log.info("Exchange【{}】创建成功","hello-java-exchange");
    }

    @Test
    public void createQueue() {
        Queue queue = new Queue("hello-java-queue",true,false,false);
        amqpAdmin.declareQueue(queue);
        log.info("Queue【{}】创建成功","hello-java-queue");
    }

    @Test
    public void createBinding() {
        Binding binding = new Binding("hello-java-queue", Binding.DestinationType.QUEUE,
                "hello-java-exchange","hello.java",null);
        amqpAdmin.declareBinding(binding);
        log.info("Binding【{}】创建成功","hello-java-binding");
    }



}
