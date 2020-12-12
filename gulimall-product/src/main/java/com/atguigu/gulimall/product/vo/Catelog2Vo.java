package com.atguigu.gulimall.product.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 二级分类 vo
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Catelog2Vo {

    //1级父分类
    private String catalog1Id;
    //三级子分类
    private List<Catelog3Vo> catalog3List;

    private String id;
    private String name;

    // 三级分类 vo
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Catelog3Vo {
        private String catalog2Id;
        private String id;
        private String name;
    }

}
