package com.devflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.entity.DepartmentMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 部门成员关联 Mapper
 */
@Mapper
public interface DepartmentMemberMapper extends BaseMapper<DepartmentMember> {
}
