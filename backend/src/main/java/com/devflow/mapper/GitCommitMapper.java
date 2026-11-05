package com.devflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.entity.GitCommit;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GitCommitMapper extends BaseMapper<GitCommit> {
}
