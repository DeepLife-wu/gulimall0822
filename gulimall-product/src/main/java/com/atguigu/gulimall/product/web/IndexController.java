package com.atguigu.gulimall.product.web;

import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;
import com.atguigu.gulimall.product.vo.Catelog2Vo;
import org.redisson.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class IndexController {

    @Autowired
    private CategoryService categoryService;
    @Autowired
    private RedissonClient redisson;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping({"/","/index.html"})
    public String indexPage(Model model) {
        List<CategoryEntity> categoryEntityList = categoryService.getLevel1Categorys();
        model.addAttribute("categorys",categoryEntityList);
        return "index";
    }

    //index/json/catalog.json
    @GetMapping("/index/catalog.json")
    @ResponseBody
    public Map<String,List<Catelog2Vo>> getCatalogJson() {
        Map<String,List<Catelog2Vo>> map = categoryService.getCatalogJson();
        return map;
    }

    @GetMapping("/hello")
    @ResponseBody
    public String hello() {
        RLock lock = redisson.getLock("my-lock");
        /**
         * 阻塞式等待
         * 锁的自动续期，如果业务超长，运行期间自动给锁续上新的30秒。不用担心业务时间长，锁自动过期被删掉
         */
        lock.lock();
        try {
            System.out.println("加锁成功，执行业务..." + Thread.currentThread().getId());
            Thread.sleep(30000);
        } catch (Exception e) {

        } finally {
            System.out.println("释放锁..." + Thread.currentThread().getId());
            lock.unlock();
        }
        return "hello";
    }

    @GetMapping("/read")
    @ResponseBody
    public String readValue() {
        RReadWriteLock lock = redisson.getReadWriteLock("rw-lock");
        String s = "";
        //加读锁
        RLock rLock = lock.readLock();
        rLock.lock();
        try {
            System.out.println("读锁加锁成功：" + Thread.currentThread().getId());
            s = redisTemplate.opsForValue().get("writeValue");
            Thread.sleep(30000);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            rLock.unlock();
            System.out.println("读锁释放：" + Thread.currentThread().getId());
        }
        return s;
    }

    @GetMapping("/write")
    @ResponseBody
    public String writeValue() {
        RReadWriteLock lock = redisson.getReadWriteLock("rw-lock");
        String s = "";
        RLock rLock = lock.writeLock();
        try {
            rLock.lock();
            System.out.println("写锁加锁成功..." + Thread.currentThread().getId());
            s = UUID.randomUUID().toString();
            Thread.sleep(30000);
            redisTemplate.opsForValue().set("writeValue",s);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            rLock.unlock();
            System.out.println("写锁释放：" + Thread.currentThread().getId());
        }
        return s;
    }

    @GetMapping("/park")
    @ResponseBody
    public String park() throws Exception {
        RSemaphore park = redisson.getSemaphore("park");
        park.acquire();
        return "ok";
    }

    @GetMapping("/go")
    @ResponseBody
    public String go() throws Exception {
        RSemaphore park = redisson.getSemaphore("park");
        park.release();
        return "ok";
    }

    /**
     * 放假，锁门
     * 1班没2 了，2，
     * 5个班全部走完，我们可以锁门
     */
    @GetMapping("/lockDoor")
    @ResponseBody
    public String lockDoor() throws InterruptedException {
        RCountDownLatch door = redisson.getCountDownLatch("door");
        door.trySetCount(5);
        door.await();
        return "放假了。。。";
    }

    @GetMapping("/gogogo/{id}")
    @ResponseBody
    public String gogogo(@PathVariable("id")Long id) {
        RCountDownLatch door = redisson.getCountDownLatch("door");
        door.countDown();//计数减1
        return id +"班的人都走了。。。";
    }


}
