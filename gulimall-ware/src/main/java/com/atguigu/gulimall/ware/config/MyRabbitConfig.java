package com.atguigu.gulimall.ware.config;

import com.google.common.collect.Maps;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class MyRabbitConfig {

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public Exchange stockEventExchange() {
        TopicExchange topicExchange = new TopicExchange("stock-event-exchange",true,false);
        return topicExchange;
    }

    @Bean
    public Queue stockReleaseStockQueue() {
        Queue queue = new Queue("stock.release.stock.queue",true,false,false);
        return queue;
    }

    @Bean
    public Queue stockDelayQueue() {
        Map<String,Object> arguments = Maps.newHashMap();
        arguments.put("x-dead-letter-exchange","stock-event-exchange");
        arguments.put("x-dead-letter-routing-key","stock.release");
        arguments.put("x-message-ttl",120000);
        Queue queue = new Queue("stock.delay.queue",true,false,false,arguments);
        return queue;
    }

    public Binding stockReleaseBinding() {
        Binding binding = new Binding("stock.release.stock.queue",
                Binding.DestinationType.QUEUE,
                "stock-event-exchange",
                "stock.release.#",null);
        return binding;
    }

    public Binding stockLockedBinding() {
        Binding binding = new Binding("stock.delay.queue",
                Binding.DestinationType.QUEUE,
                "stock-event-exchange",
                "stock.locked",null);
        return binding;
    }

}










