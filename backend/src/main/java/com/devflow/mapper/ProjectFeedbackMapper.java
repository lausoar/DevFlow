package com.devflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.entity.ProjectFeedback;
import org.apache.ibatis.annotations.Mapper;

/**
 * 项目建议反馈 Mapper
 */
@Mapper
public interface ProjectFeedbackMapper extends BaseMapper<ProjectFeedback> {
}
