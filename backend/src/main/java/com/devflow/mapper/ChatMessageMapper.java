package com.devflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 聊天消息 Mapper
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 分页查询频道消息（含发送者信息）
     */
    @Select("SELECT m.id, m.channel_id, m.sender_id, m.content, m.type, " +
            "m.reply_to_id, m.is_edited, m.is_deleted, m.created_at, " +
            "COALESCE(NULLIF(u.real_name, ''), u.username) AS sender_name, u.avatar AS sender_avatar " +
            "FROM chat_message m " +
            "LEFT JOIN sys_user u ON m.sender_id = u.id " +
            "WHERE m.channel_id = #{channelId} AND m.is_deleted = 0 " +
            "ORDER BY m.created_at ASC")
    Page<ChatMessage> selectMessagesWithUser(Page<?> page, @Param("channelId") String channelId);
}
