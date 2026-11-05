package com.devflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.entity.TaskChecklistItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskChecklistMapper extends BaseMapper<TaskChecklistItem> {
}
