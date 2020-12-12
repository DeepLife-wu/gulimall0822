package com.atguigu.gulimall.seckill.scheduled;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @EnableScheduling  开启定时任务
 * 要使任务不阻塞
 *
 * TaskSchedulingAutoConfiguration 定时任务自动配置类
 * TaskExecutionAutoConfiguration 异步任务自动配置类
 *
 * 1。可以自己使用线程池去执行
 * 2。@Async
 *
 */
@Slf4j
@Component
//@Async
//@EnableScheduling
public class HelloSchedule {


//    @Scheduled(cron = "*/5 * * * * ?")
    public void hello() {
        log.info("hello...");
    }

}










