package com.atguigu.gulimall.member.dao;

import com.atguigu.gulimall.member.entity.MemberEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员
 * 
 * @author wuchao
 * @email 15801630979@163.com
 * @date 2020-08-23 19:59:27
 */
@Mapper
public interface MemberDao extends BaseMapper<MemberEntity> {
	
}
