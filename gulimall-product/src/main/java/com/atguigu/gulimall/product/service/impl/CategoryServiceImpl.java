package com.atguigu.gulimall.product.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.gulimall.product.service.CategoryBrandRelationService;
import com.atguigu.gulimall.product.vo.Catelog2Vo;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.product.dao.CategoryDao;
import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;
import org.springframework.transaction.annotation.Transactional;


@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, CategoryEntity> implements CategoryService {
//    private Map<String,Object> cache = Maps.newHashMap();

    @Autowired
    private CategoryBrandRelationService categoryBrandRelationService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redisson;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryEntity> page = this.page(
                new Query<CategoryEntity>().getPage(params),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public List<CategoryEntity> listWithTree() {
        //1、查出所有分类
        List<CategoryEntity> entities = baseMapper.selectList(null);
        //2、组装成父子的树形结构
        //2.1）、找到所有的一级分类
        List<CategoryEntity> level1Menus = entities.stream().filter(categoryEntity ->
                categoryEntity.getParentCid() == 0
        ).map((menu)->{
            menu.setChildren(getChildrens(menu,entities));
            return menu;
        }).sorted((menu1,menu2)->{
            return (menu1.getSort()==null?0:menu1.getSort()) - (menu2.getSort()==null?0:menu2.getSort());
        }).collect(Collectors.toList());
        return level1Menus;
    }

    private List<CategoryEntity> getChildrens(CategoryEntity root,List<CategoryEntity> all) {
        List<CategoryEntity> children = all.stream().filter(categoryEntity -> {
            return categoryEntity.getParentCid() == root.getCatId();
        }).map(categoryEntity -> {
            //1、找到子菜单
            categoryEntity.setChildren(getChildrens(categoryEntity,all));
            return categoryEntity;
        }).sorted((menu1,menu2)->{
            //2、菜单的排序
            return (menu1.getSort()==null?0:menu1.getSort()) - (menu2.getSort()==null?0:menu2.getSort());
        }).collect(Collectors.toList());

        return children;
    }

    @Override
    public void removeMenuByIds(List<Long> asList) {
        //TODO:1.检查当前删除的菜单，是否被别的地方引用
        //实际物理删除不常用，设置成逻辑删除
        baseMapper.deleteBatchIds(asList);
    }

    /** 找到catelogId的完整路径
     * [2,25,225]*/
    @Override
    public Long[] findCatelogPath(Long catelogId) {
        List<Long> paths = Lists.newArrayList();
        findParentPath(catelogId,paths);
        Collections.reverse(paths);
        return paths.toArray(new Long[paths.size()]);
    }

    //225
    private void findParentPath(Long catelogId, List<Long> paths) {
        paths.add(catelogId);
        CategoryEntity byId = this.getById(catelogId);
        if(byId.getParentCid() != 0) {
            findParentPath(byId.getParentCid(),paths);
        }
    }

    //级联更新所有关联的数据
    /*@Caching(evict = {
            @CacheEvict(value = {"category"},key = "'getLevel1Categorys'"),
            @CacheEvict(value = {"category"},key = "'getCatalogJson'")
    })*/
    @CacheEvict(value = "category",allEntries = true)
    @Transactional
    @Override
    public void updateCascade(CategoryEntity category) {
        this.updateById(category);
        if(StringUtils.isNotBlank(category.getName())) {
            categoryBrandRelationService.updateCategory(category.getCatId(),category.getName());
        }
    }

    @Cacheable(value = {"category"},key = "#root.method.name",sync = true)
    @Override
    public List<CategoryEntity> getLevel1Categorys() {
        return this.baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid",0));
    }

    @Cacheable(value = "category",key = "#root.methodName",sync = true)
    @Override
    public Map<String, List<Catelog2Vo>> getCatalogJson() {
        System.out.println("查询了数据库");
        List<CategoryEntity> selectList = baseMapper.selectList(null);
        List<CategoryEntity> level1Categorys = getParent_cid(selectList, 0L);
        //2.封装数据
        Map<String, List<Catelog2Vo>> parent_cid = level1Categorys.stream().collect(Collectors.toMap(k -> k.getCatId().toString(), v -> {
            //1.每一个一级分类,查到这一个一级分类的二级分类
            List<CategoryEntity> categoryEntities = getParent_cid(selectList, v.getCatId());
            //2.封装上面的结果
            List<Catelog2Vo> catelog2Vos = null;
            if (CollectionUtils.isNotEmpty(categoryEntities)) {
                catelog2Vos = categoryEntities.stream().map(l2 -> {
                    Catelog2Vo catelog2Vo = new Catelog2Vo(v.getCatId().toString(), null, l2.getCatId().toString(), l2.getName());
                    //根据二级分类找三级分类
                    List<CategoryEntity> level3Catelog = getParent_cid(selectList, l2.getCatId());
                    if (CollectionUtils.isNotEmpty(level3Catelog)) {
                        List<Catelog2Vo.Catelog3Vo> collect = level3Catelog.stream().map(l3 -> {
                            Catelog2Vo.Catelog3Vo catelog3Vo = new Catelog2Vo.Catelog3Vo(l2.getCatId().toString(), l3.getCatId().toString(), l3.getName());
                            return catelog3Vo;
                        }).collect(Collectors.toList());
                        catelog2Vo.setCatalog3List(collect);
                    }
                    return catelog2Vo;
                }).collect(Collectors.toList());
            }
            return catelog2Vos;
        }));
        return parent_cid;
    }

    //    @Override
    public Map<String, List<Catelog2Vo>> getCatalogJson2() {
        /**
         * 1.穿结果缓存：（解决缓存穿透）
         * 2.设置过期时间（加随机值）：解决缓存雪崩
         * 3.加锁：解决缓存击穿
         */

        //1.加入缓存逻辑
        String catalogJSON = stringRedisTemplate.opsForValue().get("catalogJSON");
        if(StringUtils.isBlank(catalogJSON)) {
            //2.缓存中没有，查库
            System.out.println("缓存不命中...查询数据库...");
            Map<String, List<Catelog2Vo>> catalogJsonFromDb = getCatalogJsonFromDbWithRedissonLock();
            return catalogJsonFromDb;
        }
        System.out.println("缓存命中...直接返回...");

        //转为指定的对象
        Map<String, List<Catelog2Vo>> result = JSON.parseObject(catalogJSON,new TypeReference<Map<String, List<Catelog2Vo>>>(){});
        return result;
    }

    public Map<String, List<Catelog2Vo>> getCatalogJsonFromDbWithRedissonLock() {
        //锁的名字。锁的粒度，越细越快
        RLock lock = redisson.getLock("getCatalogJson-lock");
        lock.lock();
        try {
            Map<String, List<Catelog2Vo>> dataFromDb = getDataFromDb();
            return dataFromDb;
        } finally {
            lock.unlock();
        }
    }

    public Map<String, List<Catelog2Vo>> getCatalogJsonFromDbWithRedisLock() {
        //1。抢占分布式锁，去redis占坑
//        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent("lock", "111");
        String uuid = UUID.randomUUID().toString();
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent("lock", uuid,300,TimeUnit.SECONDS);
        if(lock) {
            //加锁成功
//            stringRedisTemplate.expire("lock",30,TimeUnit.SECONDS);
            try {
                Map<String, List<Catelog2Vo>> dataFromDb = getDataFromDb();
                return dataFromDb;
            } finally {
                //下边得是原子操作,否则还是会删到别人的锁,解决就是使脚本
                /*String lockValue = stringRedisTemplate.opsForValue().get("lock");
                if(uuid.equals(lockValue)) {
                    stringRedisTemplate.delete("lock");
                }*/
                String script = "if redis.call(\"get\",KEYS[1]) == ARGV[1] then return redis.call(\"del\",KEYS[1]) else return 0 end";
                //删锁
                Long lock1 = stringRedisTemplate.execute(new DefaultRedisScript<Long>(script, Long.class)
                        , Arrays.asList("lock"), uuid);
            }
        } else {
            //加锁失败。。。重试
            //休眠100毫秒再重试
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return getCatalogJsonFromDbWithRedisLock();//再调一次自己
        }
    }

    private Map<String, List<Catelog2Vo>> getDataFromDb() {
        String catalogJSON = stringRedisTemplate.opsForValue().get("catalogJSON");
        if (StringUtils.isNotBlank(catalogJSON)) {
            Map<String, List<Catelog2Vo>> result = JSON.parseObject(catalogJSON, new TypeReference<Map<String, List<Catelog2Vo>>>() {
            });
            return result;
        }
        System.out.println("查询了数据库。。。");

        /**
         * 1。将数据库的多次查询变为一次
         */
        List<CategoryEntity> selectList = baseMapper.selectList(null);

        //1.查出所有1级分类
//        List<CategoryEntity> level1Categorys = this.getLevel1Categorys();
        List<CategoryEntity> level1Categorys = getParent_cid(selectList, 0L);
        //2.封装数据
        Map<String, List<Catelog2Vo>> parent_cid = level1Categorys.stream().collect(Collectors.toMap(k -> k.getCatId().toString(), v -> {
            //1.每一个一级分类,查到这一个一级分类的二级分类
            List<CategoryEntity> categoryEntities = getParent_cid(selectList, v.getCatId());
            //2.封装上面的结果
            List<Catelog2Vo> catelog2Vos = null;
            if (CollectionUtils.isNotEmpty(categoryEntities)) {
                catelog2Vos = categoryEntities.stream().map(l2 -> {
                    Catelog2Vo catelog2Vo = new Catelog2Vo(v.getCatId().toString(), null, l2.getCatId().toString(), l2.getName());
                    //根据二级分类找三级分类
                    List<CategoryEntity> level3Catelog = getParent_cid(selectList, l2.getCatId());
                    if (CollectionUtils.isNotEmpty(level3Catelog)) {
                        List<Catelog2Vo.Catelog3Vo> collect = level3Catelog.stream().map(l3 -> {
                            Catelog2Vo.Catelog3Vo catelog3Vo = new Catelog2Vo.Catelog3Vo(l2.getCatId().toString(), l3.getCatId().toString(), l3.getName());
                            return catelog3Vo;
                        }).collect(Collectors.toList());
                        catelog2Vo.setCatalog3List(collect);
                    }
                    return catelog2Vo;
                }).collect(Collectors.toList());
            }
            return catelog2Vos;
        }));

        //3.查到的数据放入缓存,将对象 转成json放入
        String s = JSON.toJSONString(parent_cid);
        stringRedisTemplate.opsForValue().set("catalogJSON", s, 1, TimeUnit.DAYS);
//        cache.put("catalogJson",parent_cid);
        return parent_cid;
    }

    public synchronized Map<String, List<Catelog2Vo>> getCatalogJsonFromDbLocalLock() {
//        Map<String, List<Catelog2Vo>> catalogJson = (Map<String, List<Catelog2Vo>>)cache.get("catalogJson");
//        if(catalogJson != null) {
//            return catalogJson;
//        }

        String catalogJSON = stringRedisTemplate.opsForValue().get("catalogJSON");
        if(StringUtils.isNotBlank(catalogJSON)) {
            Map<String, List<Catelog2Vo>> result = JSON.parseObject(catalogJSON,new TypeReference<Map<String, List<Catelog2Vo>>>(){});
            return result;
        }
        System.out.println("查询了数据库。。。");

        /**
         * 1。将数据库的多次查询变为一次
         */
        List<CategoryEntity> selectList  = baseMapper.selectList(null);

        //1.查出所有1级分类
//        List<CategoryEntity> level1Categorys = this.getLevel1Categorys();
        List<CategoryEntity> level1Categorys = getParent_cid(selectList,0L);
        //2.封装数据
        Map<String, List<Catelog2Vo>> parent_cid = level1Categorys.stream().collect(Collectors.toMap(k -> k.getCatId().toString(), v -> {
            //1.每一个一级分类,查到这一个一级分类的二级分类
            List<CategoryEntity> categoryEntities = getParent_cid(selectList,v.getCatId());
            //2.封装上面的结果
            List<Catelog2Vo> catelog2Vos = null;
            if (CollectionUtils.isNotEmpty(categoryEntities)) {
                catelog2Vos = categoryEntities.stream().map(l2 -> {
                    Catelog2Vo catelog2Vo = new Catelog2Vo(v.getCatId().toString(), null, l2.getCatId().toString(), l2.getName());
                    //根据二级分类找三级分类
                    List<CategoryEntity> level3Catelog = getParent_cid(selectList,l2.getCatId());
                    if(CollectionUtils.isNotEmpty(level3Catelog)) {
                        List<Catelog2Vo.Catelog3Vo> collect = level3Catelog.stream().map(l3 -> {
                            Catelog2Vo.Catelog3Vo catelog3Vo = new Catelog2Vo.Catelog3Vo(l2.getCatId().toString(), l3.getCatId().toString(), l3.getName());
                            return catelog3Vo;
                        }).collect(Collectors.toList());
                        catelog2Vo.setCatalog3List(collect);
                    }
                    return catelog2Vo;
                }).collect(Collectors.toList());
            }
            return catelog2Vos;
        }));

        //3.查到的数据放入缓存,将对象 转成json放入
        String s = JSON.toJSONString(parent_cid);
        stringRedisTemplate.opsForValue().set("catalogJSON",s,1, TimeUnit.DAYS);
//        cache.put("catalogJson",parent_cid);
        return parent_cid;
    }

    private List<CategoryEntity> getParent_cid(List<CategoryEntity> selectList,Long parent_cid) {
//        return baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", v.getCatId()));
        return selectList.stream().filter(item->{
           return item.getParentCid() == parent_cid;
        }).collect(Collectors.toList());
    }

    /*@Override
    public Map<String, List<Catelog2Vo>> getCatalogJson() {
        //1.查出所有1级分类
        List<CategoryEntity> level1Categorys = this.getLevel1Categorys();
        //2.封装数据
        Map<String, List<Catelog2Vo>> parent_cid = level1Categorys.stream().collect(Collectors.toMap(k -> k.getCatId().toString(), v -> {
            //1.每一个一级分类,查到这一个一级分类的二级分类
            List<CategoryEntity> categoryEntities = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", v.getCatId()));
            //2.封装上面的结果
            List<Catelog2Vo> catelog2Vos = null;
            if (CollectionUtils.isNotEmpty(categoryEntities)) {
                catelog2Vos = categoryEntities.stream().map(l2 -> {
                    Catelog2Vo catelog2Vo = new Catelog2Vo(v.getCatId().toString(), null, l2.getCatId().toString(), l2.getName());
                    //根据二级分类找三级分类
                    List<CategoryEntity> level3Catelog = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("parent_cid", l2.getCatId()));
                    if(CollectionUtils.isNotEmpty(level3Catelog)) {
                        List<Catelog2Vo.Catelog3Vo> collect = level3Catelog.stream().map(l3 -> {
                            Catelog2Vo.Catelog3Vo catelog3Vo = new Catelog2Vo.Catelog3Vo(l2.getCatId().toString(), l3.getCatId().toString(), l3.getName());
                            return catelog3Vo;
                        }).collect(Collectors.toList());
                        catelog2Vo.setCatalog3List(collect);
                    }
                    return catelog2Vo;
                }).collect(Collectors.toList());
            }
            return catelog2Vos;
        }));
        return parent_cid;
    }*/
}












