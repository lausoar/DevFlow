package com.devflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.entity.WorkflowTransition;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkflowTransitionMapper extends BaseMapper<WorkflowTransition> {
}
