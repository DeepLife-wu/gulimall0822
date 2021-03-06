package com.atguigu.gulimall.ware.service;

import com.atguigu.gulimall.ware.vo.FeeVo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.gulimall.ware.entity.WareInfoEntity;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 仓库信息
 *
 * @author wuchao
 * @email 15801630979@163.com
 * @date 2020-08-23 20:02:16
 */
public interface WareInfoService extends IService<WareInfoEntity> {

    PageUtils queryPage(Map<String, Object> params);

    FeeVo getFee(Long addrId);
}

