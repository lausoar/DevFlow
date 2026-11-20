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
     * 分页查询频道消息（含发送者信息）。
     *
     * 必须是 created_at DESC：分页 1 对应"最新的一页"，前端自下而上加载更早的历史。
     * 原实现是 ASC，page=1 拿到的是频道最早 50 条，消息一旦超过一屏新消息就再也不可见。
     * 返回后由调用方反转为升序渲染。
     */
    @Select("SELECT m.id, m.channel_id, m.sender_id, m.content, m.type, " +
            "m.reply_to_id, m.is_edited, m.is_deleted, m.created_at, m.sources, " +
            "COALESCE(NULLIF(u.real_name, ''), u.username) AS sender_name, u.avatar AS sender_avatar " +
            "FROM chat_message m " +
            "LEFT JOIN sys_user u ON m.sender_id = u.id " +
            "WHERE m.channel_id = #{channelId} AND m.is_deleted = 0 " +
            "ORDER BY m.created_at DESC, m.id DESC")
    Page<ChatMessage> selectMessagesWithUser(Page<?> page, @Param("channelId") String channelId);

    /**
     * 频道内消息搜索。
     * useFullText=true 时走 MATCH ... AGAINST（需 ft_chat_message 索引）；
     * false 时回落 LIKE。两种模式共用同一条 SQL 的排序与联表逻辑。
     */
    @Select("<script>" +
            "SELECT m.id, m.channel_id, m.sender_id, m.content, m.type, " +
            "m.reply_to_id, m.is_edited, m.is_deleted, m.created_at, m.edited_at, " +
            "m.recalled_at, m.recalled_by, " +
            "COALESCE(NULLIF(u.real_name, ''), u.username) AS sender_name, u.avatar AS sender_avatar " +
            "FROM chat_message m LEFT JOIN sys_user u ON m.sender_id = u.id " +
            "WHERE m.channel_id = #{channelId} AND m.is_deleted = 0 AND m.recalled_at IS NULL " +
            "<choose>" +
            "<when test='useFullText'>" +
            "AND MATCH(m.content) AGAINST (#{keyword} IN BOOLEAN MODE) " +
            "</when>" +
            "<otherwise>" +
            "AND m.content LIKE CONCAT('%', #{keyword}, '%') " +
            "</otherwise>" +
            "</choose>" +
            "ORDER BY m.created_at DESC, m.id DESC" +
            "</script>")
    Page<ChatMessage> searchMessages(@Param("page") Page<?> page,
                                     @Param("channelId") String channelId,
                                     @Param("keyword") String keyword,
                                     @Param("useFullText") boolean useFullText);
}
