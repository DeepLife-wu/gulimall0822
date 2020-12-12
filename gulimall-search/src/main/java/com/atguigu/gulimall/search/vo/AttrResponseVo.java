package com.atguigu.gulimall.search.vo;

import lombok.Data;

@Data
public class AttrResponseVo {

    private Long attrId;

    private String attrName;

    private Integer searchType;

    private String icon;

    private String valueSelect;

    private Integer attrType;

    private Long enable;

    private Long catelogId;

    private Integer showDesc;

    private Long attrGroupId;

    /** 所属分类的名字*/
    private String catelogName;
    /** 所属分组名字*/
    private String groupName;

    private Long[] catelogPath;
}
