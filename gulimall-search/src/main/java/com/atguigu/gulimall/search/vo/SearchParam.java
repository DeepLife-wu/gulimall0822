package com.atguigu.gulimall.search.vo;

import lombok.Data;

import java.util.List;

/**
 * 封装页面所有可能传递过来的查询条件
 * catalog3Id=225&keyword=小米&sort=saleCount_asc&asStock=0/1
 *
 */
@Data
public class SearchParam {

    private String keyword;             //检索关键字
    private Long catalog3Id;            //三级分类id
    /**
     * sort=saleCount_asc/desc
     * sort=skuPrice_asc/desc
     * sort=hotScore_asc/desc
     */
    private String sort;                //排序
    /**
     * 好多的过滤条件
     * hasStock(是否有货)，skuPrice区间，brandId,catalog3Id,attrs
     * hasStock=0/1
     * skuPrice=1_500/_500/500_
     */
    private Integer hasStock;//是否只显示有货
    private String skuPrice;//价格区间查询
    private List<Long> brandId;//按品牌查询可以多选
    private List<String> attrs;//按属性筛选
    private Integer pageNum = 1;//页码

    private String _queryString;//原生的查询条件

}
