package com.atguigu.gulimall.cart.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.cart.feign.ProductFeignService;
import com.atguigu.gulimall.cart.interceptor.CartInterceptor;
import com.atguigu.gulimall.cart.service.CartService;
import com.atguigu.gulimall.cart.vo.Cart;
import com.atguigu.gulimall.cart.vo.CartItem;
import com.atguigu.gulimall.cart.vo.SkuInfoVo;
import com.atguigu.gulimall.cart.vo.UserInfoTo;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CartServiceImpl implements CartService {

    @Autowired
    StringRedisTemplate redisTemplate;

    private final String CART_PREFIX = "gulimall:cart:";

    @Autowired
    private ProductFeignService productFeignService;
    @Autowired
    private ThreadPoolExecutor executor;

    @Override
    public CartItem addToCart(Long skuId, Integer num) {
        //2.添加新商品到购物车
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        String res = (String) cartOps.get("" + skuId);
        if(StringUtils.isEmpty(res)) {
            //购物车无此商品
            //1.远程查询当前要添加的商品信息
            CartItem cartItem = new CartItem();
            CompletableFuture<Void> getSkuInfoTask = CompletableFuture.runAsync(() -> {
                R skuInfo = productFeignService.getSkuInfo(skuId);
                SkuInfoVo sku = skuInfo.getData("skuInfo", new TypeReference<SkuInfoVo>() {
                });
                cartItem.setCheck(true);
                cartItem.setCount(num);
                cartItem.setImage(sku.getSkuDefaultImg());
                cartItem.setTitle(sku.getSkuTitle());
                cartItem.setSkuId(skuId);
                cartItem.setPrice(sku.getPrice());
            },executor);

            //2.远程查询sku的组合信息
            CompletableFuture<Void> getSkuSaleAttrValues = CompletableFuture.runAsync(() -> {
                List<String> values = productFeignService.getSkuSaleAttrValues(skuId);
                cartItem.setSkuAttr(values);
            }, executor);

            String s = JSON.toJSONString(cartItem);
            cartOps.put(skuId.toString(),s);

            return cartItem;
        } else {
            //购物车有些商品，修改数量
            CartItem cartItem = JSON.parseObject(res, CartItem.class);
            cartItem.setCount(cartItem.getCount() + num);
            cartOps.put(skuId.toString(),JSON.toJSONString(cartItem));
            return cartItem;
        }
    }

    private BoundHashOperations<String, Object, Object> getCartOps() {
        UserInfoTo userInfoTo = CartInterceptor.threadLocal.get();
        String cartKey = "";
        if(userInfoTo.getUserId() != null) {
            cartKey = CART_PREFIX + userInfoTo.getUserId();
        } else {
            cartKey = CART_PREFIX + userInfoTo.getUserKey();
        }
        BoundHashOperations<String, Object, Object> operations = redisTemplate.boundHashOps(cartKey);
        return operations;
    }

    @Override
    public CartItem getCartItem(Long skuId) {
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        String str = (String)cartOps.get(skuId.toString());
        CartItem cartItem = JSON.parseObject(str, CartItem.class);
        return cartItem;
    }

    @Override
    public Cart getCart() {
        Cart cart = new Cart();
        //1.登录
        UserInfoTo userInfoTo = CartInterceptor.threadLocal.get();
        if(userInfoTo.getUserId() != null) {
            //1登录
            String cartKey = CART_PREFIX + userInfoTo.getUserId();
            //2如果临时购物车的数据还没有进行合并
            String tempCartKey = CART_PREFIX + userInfoTo.getUserKey();
            List<CartItem> tempCartItems = getCartItems(tempCartKey);
            if(CollectionUtils.isNotEmpty(tempCartItems)) {
                //临时购物车有数据需要合并
                for (CartItem item : tempCartItems) {
                    addToCart(item.getSkuId(),item.getCount());
                }
                this.clearCart(tempCartKey);
            }

            //3获取登录后的购物的数据【包含合并过来的临时购物车的数据，和登录后的购物车的数据】
            List<CartItem> cartItems = getCartItems(cartKey);
            cart.setItems(cartItems);
        } else {
            //没登录
            String cartKey = CART_PREFIX + userInfoTo.getUserKey();
            //获取临时购物车的所有购物项
            List<CartItem> cartItems = getCartItems(cartKey);
            cart.setItems(cartItems);
        }
        return cart;
    }

    private List<CartItem> getCartItems(String cartKey) {
        BoundHashOperations<String, Object, Object> hasOps = redisTemplate.boundHashOps(cartKey);
        List<?> values = hasOps.values();
        if(CollectionUtils.isNotEmpty(values)) {
            List<CartItem> collect = values.stream().map((obj) -> {
                String str = (String)obj;
                CartItem cartItem = JSON.parseObject(str, CartItem.class);
                return cartItem;
            }).collect(Collectors.toList());
//            cart.setItems(collect);
            return collect;
        }
        return null;
    }

    @Override
    public void clearCart(String cartKey) {
        redisTemplate.delete(cartKey);
    }

    @Override
    public void checkItem(Long skuId, Integer check) {
        CartItem cartItem = getCartItem(skuId);
        cartItem.setCheck(check == 1 ? true : false);
        String s = JSON.toJSONString(cartItem);
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        cartOps.put(skuId.toString(),s);
    }

    @Override
    public void countItemCount(Long skuId, Integer num) {
        CartItem cartItem = getCartItem(skuId);
        cartItem.setCount(num);
        String s = JSON.toJSONString(cartItem);
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        cartOps.put(skuId.toString(),s);
    }

    @Override
    public void deleteItem(Long skuId) {
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        cartOps.delete(skuId.toString());
    }

    @Override
    public List<CartItem> getUserCartItems() {
        UserInfoTo userInfoTo = CartInterceptor.threadLocal.get();
        if(userInfoTo.getUserId() == null) {
            return null;
        }

        //获取所有被选中的购物项
        String cartKey = CART_PREFIX + userInfoTo.getUserId();
        List<CartItem> cartItems = getCartItems(cartKey);
        List<CartItem> collect = cartItems.stream().filter(item -> item.getCheck())
                .map(item->{
                    R r = productFeignService.getPrice(item.getSkuId());
                    //更新为最新的价格
                    String price = (String)r.get("data");
                    item.setPrice(new BigDecimal(price));
                    return item;
                })
                .collect(Collectors.toList());
        return collect;
    }
}
