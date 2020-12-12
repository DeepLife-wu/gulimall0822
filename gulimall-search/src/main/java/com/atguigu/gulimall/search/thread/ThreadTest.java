package com.atguigu.gulimall.search.thread;

import java.util.concurrent.*;

public class ThreadTest {
    public static ExecutorService service = Executors.newFixedThreadPool(10);

    public static void main(String[] args) throws Exception {
        System.out.println("main... start...");

//        CompletableFuture.runAsync(()->{
//            System.out.println("当前线程：" + Thread.currentThread().getId());
//            int i = 10 / 2;
//            System.out.println("运行结果：" + i);
//        },service);

        CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
            System.out.println("当前线程：" + Thread.currentThread().getId());
            int i = 10 / 2;
            System.out.println("运行结果：" + i);
            return i;
        }, service).whenComplete((result,exception)->{
            System.out.println("异步任务成功完成了... 结果是：" + result + "；异常是：" + exception);
        }).exceptionally(throwable -> {
            return 10;
        });

//        Integer integer = future.get();
        System.out.println("main... end..." );
    }

    public static void testThread() {
        /*System.out.println("main... start...");
        Thread01 thread = new Thread01();
        thread.start();
        System.out.println("main... end...");*/

        /*System.out.println("main... start...");
        Runnable01 runnable01 = new Runnable01();
        new Thread(runnable01).start();
        System.out.println("main... end...");*/

        System.out.println("main... start...");
        /*FutureTask<Integer> futureTask = new FutureTask<>(new Callable01());
        new Thread(futureTask).start();*/
//        service.execute(new Runnable01());

        ThreadPoolExecutor executor = new ThreadPoolExecutor(5,
                200,
                10,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100000),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy() );

        Executors.newCachedThreadPool();

        System.out.println("main... end...");
    }

    public static class Thread01 extends Thread {
        @Override
        public void run() {
            System.out.println("当前线程：" + Thread.currentThread().getId());
            int i = 10 / 2;
            System.out.println("运行结果：" + i);
        }
    }

    public static class Runnable01 implements Runnable {

        @Override
        public void run() {
            System.out.println("当前线程：" + Thread.currentThread().getId());
            int i = 10 / 2;
            System.out.println("运行结果：" + i);
        }
    }

    public static class Callable01 implements Callable<Integer> {

        @Override
        public Integer call() throws Exception {
            System.out.println("当前线程：" + Thread.currentThread().getId());
            int i = 10 / 2;
            System.out.println("运行结果：" + i);
            return i;
        }
    }
}
