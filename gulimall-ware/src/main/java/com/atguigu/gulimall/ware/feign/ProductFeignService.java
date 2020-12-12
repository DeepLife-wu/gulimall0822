package com.atguigu.gulimall.ware.feign;

import com.atguigu.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;


@FeignClient("gulimall-product")
public interface ProductFeignService {

    @GetMapping("/product/skuinfo/info/{skuId}")
    R info(@PathVariable("skuId")Long skuId);

}
