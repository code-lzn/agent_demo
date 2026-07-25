package com.limou.agent_demo.mapper;

import com.limou.agent_demo.entity.Message;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface MessageMapper {

    @Insert("INSERT INTO message (id, conversation_id, role, content, tool_calls) " +
            "VALUES (#{id}, #{conversationId}, #{role}, #{content}, #{toolCalls})")
    void insert(Message msg);

    @Select("SELECT * FROM message WHERE conversation_id = #{conversationId} ORDER BY created_at ASC")
    List<Message> selectByConversationId(String conversationId);

    @Select("SELECT * FROM message WHERE conversation_id = #{conversationId} " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<Message> selectRecentByConversationId(@Param("conversationId") String conversationId,
                                               @Param("limit") int limit);

    @Delete("DELETE FROM message WHERE conversation_id = #{conversationId}")
    int deleteByConversationId(String conversationId);

    @Select("SELECT COUNT(*) FROM message WHERE conversation_id = #{conversationId}")
    int countByConversationId(String conversationId);

    @Select("SELECT content FROM message WHERE conversation_id = #{conversationId} AND role = 'user' ORDER BY created_at ASC LIMIT 1")
    String findFirstUserMessageContent(String conversationId);
}
