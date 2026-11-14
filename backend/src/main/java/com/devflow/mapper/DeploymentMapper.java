package com.devflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.entity.Deployment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeploymentMapper extends BaseMapper<Deployment> {
}
