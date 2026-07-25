package com.limou.agent_demo.mapper;

import com.limou.agent_demo.entity.Conversation;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ConversationMapper {

    @Insert("INSERT INTO conversation (id, title, model) VALUES (#{id}, #{title}, #{model})")
    void insert(Conversation conv);

    @Select("SELECT * FROM conversation WHERE id = #{id}")
    Conversation selectById(String id);

    @Select("SELECT * FROM conversation ORDER BY updated_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<Conversation> selectAll(@Param("offset") int offset, @Param("limit") int limit);

    @Delete("DELETE FROM conversation WHERE id = #{id}")
    int deleteById(String id);

    @Update("UPDATE conversation SET title = #{title} WHERE id = #{id}")
    int updateTitle(@Param("id") String id, @Param("title") String title);
}
