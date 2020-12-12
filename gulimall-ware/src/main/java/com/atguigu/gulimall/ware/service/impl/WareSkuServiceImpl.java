package com.atguigu.gulimall.ware.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.exception.NoStockException;
import com.atguigu.common.to.SkuHasStockVo;
import com.atguigu.common.to.mq.OrderTo;
import com.atguigu.common.to.mq.StockDetailTo;
import com.atguigu.common.to.mq.StockLockedTo;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.ware.entity.WareOrderTaskDetailEntity;
import com.atguigu.gulimall.ware.entity.WareOrderTaskEntity;
import com.atguigu.gulimall.ware.feign.OrderFeignService;
import com.atguigu.gulimall.ware.feign.ProductFeignService;
import com.atguigu.gulimall.ware.service.WareOrderTaskDetailService;
import com.atguigu.gulimall.ware.service.WareOrderTaskService;
import com.atguigu.gulimall.ware.vo.OrderItemVo;
import com.atguigu.gulimall.ware.vo.OrderVo;
import com.atguigu.gulimall.ware.vo.WareSkuLockVo;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import lombok.Data;
import org.apache.commons.lang.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.ware.dao.WareSkuDao;
import com.atguigu.gulimall.ware.entity.WareSkuEntity;
import com.atguigu.gulimall.ware.service.WareSkuService;
import org.springframework.transaction.annotation.Transactional;


@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuDao, WareSkuEntity> implements WareSkuService {

    @Autowired
    private WareSkuDao wareSkuDao;
    @Autowired
    private ProductFeignService productFeignService;
    @Autowired
    private OrderFeignService orderFeignService;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private WareOrderTaskService orderTaskService;
    @Autowired
    private WareOrderTaskDetailService orderTaskDetailService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        QueryWrapper<WareSkuEntity> wrapper = new QueryWrapper<>();
        String skuId = (String) params.get("skuId");
        if(StringUtils.isNotBlank(skuId)) {
            wrapper.eq("sku_id",skuId);
        }
        String wareId = (String) params.get("wareId");
        if(StringUtils.isNotBlank(wareId)) {
            wrapper.eq("ware_id",wareId);
        }

        IPage<WareSkuEntity> page = this.page(
                new Query<WareSkuEntity>().getPage(params),
                wrapper
        );

        return new PageUtils(page);
    }

    @Transactional
    @Override
    public void addStock(Long skuId, Long wareId, Integer skuNum) {
        //1.判断如果 还没有这个库存记录则新增
        List<WareSkuEntity> entities = wareSkuDao.selectList(new QueryWrapper<WareSkuEntity>().eq("sku_id", skuId)
                .eq("ware_id", wareId));
        if(CollectionUtils.isEmpty(entities)) {
            WareSkuEntity skuEntity = new WareSkuEntity();
            skuEntity.setSkuId(skuId);
            skuEntity.setWareId(wareId);
            skuEntity.setStock(skuNum);
            skuEntity.setStockLocked(0);
            try {
                //TODO:远程查询sku的名字,如果失败不需回滚
                //1.自己捕获
                //TODO:2.还可以用什么办法让异常出现以后不回滚？
                R info = productFeignService.info(skuId);
                if(info.getCode() == 0) {
                    Map<String,Object> skuInfo = (Map<String,Object>)info.get("skuInfo");
                    skuEntity.setSkuName((String) skuInfo.get("skuName"));
                }
            } catch (Exception e) {}

            wareSkuDao.insert(skuEntity);
        } else {
            wareSkuDao.addStock(skuId,wareId,skuNum);
        }
    }

    @Override
    public List<SkuHasStockVo> getSkusHasStock(List<Long> skuIds) {
        List<SkuHasStockVo> collect = skuIds.stream().map(skuId -> {
            SkuHasStockVo vo = new SkuHasStockVo();
            /** select sum(stock-stock_locked) from wms_ware_sku where sku_id=1 */
            //查询当前sku 的总库存量
            Long count = this.baseMapper.getSkuStock(skuId);
            vo.setSkuId(skuId);
            vo.setHasStock(count == null ? false : count > 0);
            return vo;
        }).collect(Collectors.toList());
        return collect;
    }

    /** 为某个订单锁订库存*/
    /**
     * 库存解锁的场景
     * 1）下订单成功，订单过期没有支付被系统自动取消，被用户手动取消。都要解锁库存
     * 2）下订单成功，库存锁定成功，接下来的业务调用失败，导致订单回滚。
     * 之前锁定的库存就要自动解锁
     *
     * @param vo
     * @return
     */
    @Transactional(rollbackFor = NoStockException.class)
    @Override
    public Boolean orderLockStock(WareSkuLockVo vo) {
        /**
         * 保存库存工作单的详情
         */
        WareOrderTaskEntity taskEntity = new WareOrderTaskEntity();
        taskEntity.setOrderSn(vo.getOrderSn());
        orderTaskService.save(taskEntity);

        //1.按照下单的收货地址，找到一个就近仓库，锁定库存。
        //1.找到每个商品在哪个仓库都有库存
        List<OrderItemVo> locks = vo.getLocks();
        List<SkuWareHasStock> collect = locks.stream().map(item -> {
            SkuWareHasStock stock = new SkuWareHasStock();
            Long skuId = item.getSkuId();
            stock.setSkuId(skuId);
            stock.setNum(item.getCount());
            //查询这个商品在哪里有库存
            List<Long> wareIds = wareSkuDao.listWareIdHasSkuStock(skuId);
            stock.setWareId(wareIds);
            return stock;
        }).collect(Collectors.toList());

        //2.锁定库存
        for (SkuWareHasStock hasStock : collect) {
            Boolean skuStocked = false;
            Long skuId = hasStock.getSkuId();
            List<Long> wareIds = hasStock.getWareId();
            if(CollectionUtils.isNotEmpty(wareIds)) {
                //没有任何仓库有这个商品的库存
                throw new NoStockException(skuId);
            }
            //1。如果每一个商品都锁定成功，将当前商品锁定了几件的工作单记录发送给mq。
            //2。锁定失败。前边保存的工作单信息回滚。发送出去的消自习，即使要解锁记录，由于去数据库查不到id
            //
            for (Long wareId : wareIds) {
                Long count = wareSkuDao.lockSkuStock(skuId,wareId,hasStock.getNum());
                if(count == 1) {
                   skuStocked = true;
                   //TODO:告诉mq库存锁定成功 rabbitTemplate
                    WareOrderTaskDetailEntity detailEntity = new WareOrderTaskDetailEntity(null, skuId, "", hasStock.getNum(), taskEntity.getId(),wareId,1);
                    orderTaskDetailService.save(detailEntity);
                    StockLockedTo stockLockedTo = new StockLockedTo();
                    stockLockedTo.setId(taskEntity.getId());
                    StockDetailTo stockDetailTo = new StockDetailTo();
                    BeanUtils.copyProperties(detailEntity,stockDetailTo);
                    stockLockedTo.setDetail(stockDetailTo);//只发id不行，防止回滚以后找不到数据
                    rabbitTemplate.convertAndSend("stock-event-exchange","stock.locked",stockLockedTo);
                    break;
                } else {
                    //当前仓库锁失败，重试下一个仓库
                }
            }
            if(!skuStocked) {
                //当前商品所有仓库没锁住
                throw new NoStockException(skuId);
            }
        }
        //3.肯定全部都是锁定成功过的
        return Boolean.TRUE;
    }

    @Data
    public static class SkuWareHasStock {
        private Long skuId;
        private Integer num;
        private List<Long> wareId;
    }

    @Override
    public void unlockStock(StockLockedTo to) {
        StockDetailTo detail = to.getDetail();
        Long detailId = detail.getId();
        /**
         * 解锁
         * 1.查询数据库关于这个订单的锁定库存信息
         * 有 (证明库存锁定成功了)
         *      解锁：订单情况
         *          没有订单。必须解人济山庄
         *          有订单。不是解锁存库。（看订单状态：已取消：解锁存库，没取消：不能解锁）
         * 没有：库存锁定失败了，库存回滚了。这种情况无需解锁
         */
        WareOrderTaskDetailEntity byId = orderTaskDetailService.getById(detailId);
        if (byId != null) {
            //解锁
            Long id = to.getId();
            WareOrderTaskEntity taskEntity = orderTaskService.getById(id);
            String orderSn = taskEntity.getOrderSn();//根据订单号，查订单状态
            R r = orderFeignService.getOrderStatus(orderSn);
            if (r.getCode() == 0) {
                //订单数据返回成功
                OrderVo data = r.getData(new TypeReference<OrderVo>() {
                });
                if (data == null || data.getStatus() == 4) {
                    //订单不存在
                    //订单已经被取消了。才能解锁库存
                    if(byId.getLockStatus() == 1) {
                        //当前库存工作单详情，状态1 已锁定但是未解锁才可以解锁
                        unLockStock(detail.getSkuId(), detail.getWareId(), detail.getSkuNum(), detailId);
                    }

                }
            } else {
                //消息拒绝后重新放入队列，让别人继续消费解锁
                throw new RuntimeException("远程服务调用失败！");
            }
        } else {
            //无需解锁
        }
    }

    //防止订单服务卡顿，导致订单状态消息一直改不了，库存消息优先到期。查订单状态 新建状态 什么都不做就走了
    //导致卡顿的订单，永远不能解锁库存
    @Transactional
    @Override
    public void unlockStock(OrderTo orderTo) {
        String orderSn = orderTo.getOrderSn();
        //查一下最新库存的状态，防止重复解锁库存
        WareOrderTaskEntity task = orderTaskService.getOrderTaskByOrderSn(orderSn);
        //按工作单找到所有没有解锁的库存 进行解锁
        Long id = task.getId();
        List<WareOrderTaskDetailEntity> entities = orderTaskDetailService.list(new QueryWrapper<WareOrderTaskDetailEntity>()
                .eq("task_id", id)
                .eq("lock_status", 1));
        for (WareOrderTaskDetailEntity entity : entities) {
            unLockStock(entity.getSkuId(),entity.getWareId(),entity.getSkuNum(),entity.getId());
        }
    }

    private void unLockStock(Long skuId, Long wareId, Integer num, Long taskDetailId) {
        //库存解锁
        wareSkuDao.unlockStock(skuId,wareId,num);
        //更新库存工作单的状态
        WareOrderTaskDetailEntity entity = new WareOrderTaskDetailEntity();
        entity.setId(taskDetailId);
        entity.setLockStatus(2);//已解锁
        orderTaskDetailService.updateById(entity);
    }

}