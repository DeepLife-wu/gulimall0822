package com.atguigu.gulimall.order.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.OrderConstant;
import com.atguigu.common.constant.OrderStatusEnum;
import com.atguigu.common.exception.NoStockException;
import com.atguigu.common.to.mq.OrderTo;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.MemberResponseVo;
import com.atguigu.gulimall.order.entity.OrderItemEntity;
import com.atguigu.gulimall.order.entity.PaymentInfoEntity;
import com.atguigu.gulimall.order.feign.CartFeignService;
import com.atguigu.gulimall.order.feign.MemberFeignService;
import com.atguigu.gulimall.order.feign.ProductFeignService;
import com.atguigu.gulimall.order.feign.WmsFeignService;
import com.atguigu.gulimall.order.interceptor.LoginUserInterceptor;
import com.atguigu.gulimall.order.service.OrderItemService;
import com.atguigu.gulimall.order.service.PaymentInfoService;
import com.atguigu.gulimall.order.to.OrderCreateTo;
import com.atguigu.gulimall.order.vo.*;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.google.common.collect.Lists;
import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.order.dao.OrderDao;
import com.atguigu.gulimall.order.entity.OrderEntity;
import com.atguigu.gulimall.order.service.OrderService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;


@Service("orderService")
public class OrderServiceImpl extends ServiceImpl<OrderDao, OrderEntity> implements OrderService {
    private ThreadLocal<OrderSubmitVo> confirmVoThreadLocal = new ThreadLocal<>();

    @Autowired
    private OrderItemService orderItemService;

    @Autowired
    private CartFeignService cartFeignService;
    @Autowired
    private WmsFeignService wmsFeignService;
    @Autowired
    private ProductFeignService productFeignService;
    @Autowired
    private MemberFeignService memberFeignService;
    @Autowired
    private PaymentInfoService paymentInfoService;

    @Autowired
    private ThreadPoolExecutor executor;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<OrderEntity> page = this.page(
                new Query<OrderEntity>().getPage(params),
                new QueryWrapper<OrderEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public OrderConfirmVo confirmOrder() throws ExecutionException, InterruptedException {
        OrderConfirmVo confirmVo = new OrderConfirmVo();
        MemberResponseVo memberResponseVo = LoginUserInterceptor.loginUser.get();

        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        CompletableFuture<Void> getAddressFuture = CompletableFuture.runAsync(() -> {
            RequestContextHolder.setRequestAttributes(requestAttributes);
            //1.远程查询所有的收货地址列表
            List<MemberAddressVo> address = memberFeignService.getAddress(memberResponseVo.getId());
            confirmVo.setAddress(address);
        }, executor);

        CompletableFuture<Void> cartFuture = CompletableFuture.runAsync(() -> {
            RequestContextHolder.setRequestAttributes(requestAttributes);
            //2.远程查询购物车所有选中的购物项
            List<OrderItemVo> items = cartFeignService.getCurrentUserCartItems();
            confirmVo.setItems(items);
        }, executor).thenRunAsync(()->{
            List<OrderItemVo> items = confirmVo.getItems();
            List<Long> collect = items.stream().map(item -> item.getSkuId()).collect(Collectors.toList());

            R hasStock = wmsFeignService.getSkusHasStock(collect);
            List<SkuStockVo> data = hasStock.getData(new TypeReference<List<SkuStockVo>>() {
            });
            if(data != null) {
                Map<Long, Boolean> map = data.stream().collect(Collectors.toMap(SkuStockVo::getSkuId, SkuStockVo::getHasStock));
                confirmVo.setStocks(map);
            }
        },executor);


        //3.查询用户积分
        Integer integration = memberResponseVo.getIntegration();
        confirmVo.setIntegration(integration);

        //4.其它数据自动计算

        //5.使用redis制作防重令牌
        String token = UUID.randomUUID().toString().replace("-", "");
        confirmVo.setOrderToken(token);
        redisTemplate.opsForValue().set(OrderConstant.USER_ORDER_TOKEN_PREFIX + memberResponseVo.getId()
                ,token,30, TimeUnit.MINUTES);

        CompletableFuture.allOf(getAddressFuture,cartFuture).get();
        return confirmVo;
    }

    @GlobalTransactional
    @Transactional
    @Override
    public SubmitOrderResponseVo submitOrder(OrderSubmitVo vo) {
        SubmitOrderResponseVo response = new SubmitOrderResponseVo();
        response.setCode(0);
        //1.验证令牌【令牌的对比和删除必须保证原子性】
        MemberResponseVo memberResponseVo = LoginUserInterceptor.loginUser.get();
        confirmVoThreadLocal.set(vo);
        String orderToken = vo.getOrderToken();
        //原子验证令牌和删除令牌
        String script = "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end";
        Long result = redisTemplate.execute(new DefaultRedisScript<Long>(script, Long.class),
                Arrays.asList(OrderConstant.USER_ORDER_TOKEN_PREFIX + memberResponseVo.getId()),
                orderToken);
        if(result == 0) {//验证失败
            response.setCode(1);
            return response;
        } else {
            //令牌验证成功
            //下单：去创建订单，验令牌，验价格，锁库存...
            //1.创建订单，订单项等信息
            OrderCreateTo order = createOrder();
            //2.验价
            BigDecimal payAmount = order.getOrder().getPayAmount();
            BigDecimal payPrice = vo.getPayPrice();
            if(Math.abs(payAmount.subtract(payPrice).doubleValue()) < 0.01) {
                //3.保存订单
                saveOrder(order);
                //4.库存锁定 ,只要有异常回滚订单数据
                //订单号，所有订单项（skuId,skuName,num）
                WareSkuLockVo lockVo = new WareSkuLockVo();
                lockVo.setOrderSn(order.getOrder().getOrderSn());
                List<OrderItemVo> locks = order.getOrderItems().stream().map(item -> {
                    OrderItemVo itemVo = new OrderItemVo();
                    itemVo.setSkuId(item.getSkuId());
                    itemVo.setCount(item.getSkuQuantity());
                    itemVo.setTitle(item.getSkuName());
                    return itemVo;
                }).collect(Collectors.toList());
                lockVo.setLocks(locks);
                R r = wmsFeignService.orderLockStock(lockVo);
                if(r.getCode() == 0) {
                    //锁成功
                    response.setOrder(order.getOrder());

                    //TODO 5 远程扣减积分 出异常
                    //TODO 订单创建成功发送消息给mq
                    rabbitTemplate.convertAndSend("order-event-exchange","order.create.order",order.getOrder());
                    return response;
                } else {
                    //锁定失败
                    String msg = (String)r.get("msg");
                    throw new NoStockException(msg);
                }
            } else {
                response.setCode(2);
                return response;
            }
        }

        /*String redisToken = redisTemplate.opsForValue().get(OrderConstant.USER_ORDER_TOKEN_PREFIX + memberResponseVo.getId());
        if(StringUtils.isNotBlank(orderToken) && orderToken.equals(redisToken)) {
        } else {
        }*/
    }

    @Override
    public OrderEntity getOrderByOrderSn(String orderSn) {
        OrderEntity order = this.getOne(new QueryWrapper<OrderEntity>().eq("order_sn", orderSn));
        return order;
    }

    @Override
    public void closeOrder(OrderEntity entity) {
        //查询当前这个订单的最新状态
        OrderEntity orderEntity = this.getById(entity.getId());
        if(orderEntity.getStatus() == OrderStatusEnum.CREATE_NEW.getCode()) {
            //关单
            OrderEntity updateOrder = new OrderEntity();
            updateOrder.setId(orderEntity.getId());
            updateOrder.setStatus(OrderStatusEnum.CANCLED.getCode());
            this.updateById(updateOrder);

            OrderTo orderTo = new OrderTo();
            BeanUtils.copyProperties(orderEntity,orderTo);

            //发给mq一个
            try {
                rabbitTemplate.convertAndSend("order-event-exchange","order.release.other",orderTo);
            } catch (Exception e) {
                //TODO 将没法送成功的消息进行重试发送

            }

        }
    }

    @Override
    public PayVo getOrderPay(String orderSn) {
        PayVo payVo = new PayVo();
        OrderEntity order = this.getOrderByOrderSn(orderSn);

        payVo.setOut_trade_no(order.getOrderSn());//订单号
        BigDecimal bigDecimal = order.getPayAmount().setScale(2, BigDecimal.ROUND_UP);
        payVo.setTotal_amount(bigDecimal.toString());

        List<OrderItemEntity> order_sn = orderItemService.list(new QueryWrapper<OrderItemEntity>().eq("order_sn", orderSn));
        OrderItemEntity orderItem = order_sn.get(0);
        payVo.setSubject(orderItem.getSkuName());//订单的主题
        payVo.setBody(orderItem.getSkuAttrsVals());//订单的备注
        return payVo;
    }

    @Override
    public PageUtils queryPageWithItem(Map<String, Object> params) {
        MemberResponseVo memberResponseVo = LoginUserInterceptor.loginUser.get();
        IPage<OrderEntity> page = this.page(
                new Query<OrderEntity>().getPage(params),
                new QueryWrapper<OrderEntity>().eq("member_id",memberResponseVo.getId()).orderByDesc("id")
        );
        List<OrderEntity> orderEntities = page.getRecords().stream().map(order -> {
            List<OrderItemEntity> itemEntities = orderItemService.list(new QueryWrapper<OrderItemEntity>().eq("order_sn", order.getOrderSn()));
            order.setItemEntities(itemEntities);
            return order;
        }).collect(Collectors.toList());
        page.setRecords(orderEntities);
        return new PageUtils(page);
    }

    @Override
    public String handlePayResult(PayAsyncVo vo) {
        //1.保存交易流水
        PaymentInfoEntity infoEntity = new PaymentInfoEntity();
        infoEntity.setAlipayTradeNo(vo.getTrade_no());
        infoEntity.setOrderSn(vo.getOut_trade_no());
        infoEntity.setPaymentStatus(vo.getTrade_status());
        infoEntity.setCallbackTime(vo.getNotify_time());
        paymentInfoService.save(infoEntity);

        //2.修改订单的状态
        if(vo.getTrade_status().equals("TRADE_SUCCESS") || vo.getTrade_status().equals("TRADE_FINISHED") ) {
            //支付成功状态
            String outTradeNo = vo.getOut_trade_no();
            this.baseMapper.updateOrderPayedStatus(outTradeNo,OrderStatusEnum.PAYED.getCode());
        };
        return "success";
    }

    /**
     * 保存订单数据
     * @param order
     */
    private void saveOrder(OrderCreateTo order) {
        OrderEntity orderEntity = order.getOrder();
        orderEntity.setModifyTime(new Date());
        this.save(orderEntity);

        List<OrderItemEntity> orderItems = order.getOrderItems();
        orderItemService.saveBatch(orderItems);
    }

    private OrderCreateTo createOrder() {
        OrderCreateTo createTo = new OrderCreateTo();
        //1.生成一个订单号
        String orderSn = IdWorker.getTimeId();
        OrderEntity orderEntity = buildOrder(orderSn);

        //2.获取所有的订单项
        List<OrderItemEntity> itemEntities = buildOrderItems(orderSn);

        //3.计算价格相关
        computePrice(orderEntity,itemEntities);
        createTo.setOrder(orderEntity);
        createTo.setOrderItems(itemEntities);

        return createTo;

    }

    private void computePrice(OrderEntity orderEntity, List<OrderItemEntity> itemEntities) {
        //1.订单价格相关
        BigDecimal total = new BigDecimal("0.0");
        BigDecimal coupon = new BigDecimal("0.0");
        BigDecimal integration = new BigDecimal("0.0");
        BigDecimal promotion = new BigDecimal("0.0");
        BigDecimal gift = new BigDecimal("0.0");
        BigDecimal growth = new BigDecimal("0.0");

        for (OrderItemEntity entity : itemEntities) {
            coupon = coupon.add(entity.getCouponAmount());
            integration = integration.add(entity.getIntegrationAmount());
            promotion = promotion.add(entity.getPromotionAmount());
            total = total.add(entity.getRealAmount());
            gift = gift.add(new BigDecimal(entity.getGiftIntegration().toString()));
            growth = growth.add(new BigDecimal(entity.getGiftGrowth().toString()));
        }
        orderEntity.setTotalAmount(total);
        //总额+运费 = 应付总额
        orderEntity.setPayAmount(total.add(orderEntity.getFreightAmount()));
        orderEntity.setPromotionAmount(promotion);
        orderEntity.setIntegrationAmount(integration);
        orderEntity.setCouponAmount(coupon);

        //设置积分等信息
        orderEntity.setIntegration(gift.intValue());
        orderEntity.setGrowth(growth.intValue());
        orderEntity.setDeleteStatus(0); //未删除
    }

    private OrderEntity buildOrder(String orderSn) {
        MemberResponseVo member = LoginUserInterceptor.loginUser.get();
        OrderEntity entity = new OrderEntity();
        entity.setOrderSn(orderSn);
        entity.setMemberId(member.getId());

        //获取收货地址信息
        OrderSubmitVo submitVo = confirmVoThreadLocal.get();
        R fee = wmsFeignService.getFee(submitVo.getAddrId());
        FeeVo feeResp = fee.getData(new TypeReference<FeeVo>() {
        });
        //设置运费信息
        entity.setFreightAmount(feeResp.getFee());
        //设置收货人信息
        entity.setReceiverCity(feeResp.getAddress().getCity());
        entity.setReceiverDetailAddress(feeResp.getAddress().getDetailAddress());
        entity.setReceiverName(feeResp.getAddress().getName());
        entity.setReceiverPhone(feeResp.getAddress().getPhone());
        entity.setReceiverPostCode(feeResp.getAddress().getPostCode());
        entity.setReceiverProvince(feeResp.getAddress().getProvince());
        entity.setReceiverRegion(feeResp.getAddress().getRegion());

        //设置订单状态信息
        entity.setStatus(OrderStatusEnum.CREATE_NEW.getCode());
        entity.setAutoConfirmDay(7);

        return entity;
    }

    /** 构建所有订单项*/
    private List<OrderItemEntity> buildOrderItems(String orderSn) {
        List<OrderItemVo> currentUserCartItems = cartFeignService.getCurrentUserCartItems();
        if(CollectionUtils.isNotEmpty(currentUserCartItems)) {
            List<OrderItemEntity> itemEntities = currentUserCartItems.stream().map(cartItem -> {
                OrderItemEntity itemEntity =  buildOrderItem(cartItem);
                //1.订单信息：订单号
                itemEntity.setOrderSn(orderSn);
                return itemEntity;
            }).collect(Collectors.toList());
            return itemEntities;
        }
        return Lists.newArrayList();
    }

    /** 构建某一个订单项的内容*/
    private OrderItemEntity buildOrderItem(OrderItemVo cartItem) {
        OrderItemEntity itemEntity = new OrderItemEntity();
        //2.商品的SPU信息
        Long skuId = cartItem.getSkuId();
        R r = productFeignService.getSpuInfoBySkuId(skuId);
        SpuInfoVo data = r.getData(new TypeReference<SpuInfoVo>() {
        });
        itemEntity.setSpuId(data.getId());
        itemEntity.setSpuBrand(data.getBrandId().toString());
        itemEntity.setSpuName(data.getSpuName());
        itemEntity.setCategoryId(data.getCatalogId());

        //3.商品的sku信息
        itemEntity.setSkuId(cartItem.getSkuId());
        itemEntity.setSkuName(cartItem.getTitle());
        itemEntity.setSkuPic(cartItem.getImage());
        itemEntity.setSkuPrice(cartItem.getPrice());
        String skuAttr = StringUtils.collectionToDelimitedString(cartItem.getSkuAttr(), ";");
        itemEntity.setSkuAttrsVals(skuAttr);
        itemEntity.setSkuQuantity(cartItem.getCount());
        //4.优惠信息【不做】
        //5.积分信息
        int growth = cartItem.getPrice().multiply(new BigDecimal(cartItem.getCount().toString())).intValue();
        itemEntity.setGiftGrowth(growth);
        itemEntity.setGiftIntegration(growth);

        //6.订单基价格信息
        itemEntity.setPromotionAmount(new BigDecimal("0"));
        itemEntity.setCouponAmount(new BigDecimal("0"));
        itemEntity.setIntegrationAmount(new BigDecimal("0"));
        //当前订单项的实际金额。 总额-各种优惠
        BigDecimal origin = itemEntity.getSkuPrice().multiply(new BigDecimal(itemEntity.getSkuQuantity().toString()));
        BigDecimal percentPrice = origin.subtract(itemEntity.getCouponAmount())
                .subtract(itemEntity.getPromotionAmount())
                .subtract(itemEntity.getIntegrationAmount());
        itemEntity.setRealAmount(percentPrice);
        return itemEntity;
    }
}