package com.devflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.entity.DesignAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DesignAssetMapper extends BaseMapper<DesignAsset> {

    /**
     * 带前置状态的原子流转：两个并发评审（例如同时通过和打回）只有一个能成功。
     * 返回 0 表示状态已被他人改变，调用方必须放弃本次通知。
     */
    @Update("UPDATE design_asset SET status = #{to}, updated_at = NOW(3) "
            + "WHERE id = #{id} AND status = #{from} AND deleted = 0")
    int transitionStatus(@Param("id") String id,
                         @Param("from") String from,
                         @Param("to") String to);
}
