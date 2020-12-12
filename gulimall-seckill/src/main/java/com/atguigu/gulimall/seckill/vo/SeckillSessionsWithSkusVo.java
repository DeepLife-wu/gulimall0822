package com.atguigu.gulimall.seckill.vo;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class SeckillSessionsWithSkusVo {
    private Long id;
    private String name;
    private Date startTime;
    private Date endTime;
    private Integer status;
    private Date createTime;

    private List<SeckillSkuVo> relationSkus;
}
