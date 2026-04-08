package cn.cctstudio.nexacord.repository;

import cn.cctstudio.nexacord.model.Channel;
import cn.cctstudio.nexacord.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByChannel(Channel channel);
    List<Message> findByChannelId(Long channelId);

    @Query("""
            select m from Message m
            where m.channel.id = :channelId and (m.deleted is null or m.deleted = false)
            order by m.createdAt desc
            """)
    List<Message> findVisibleByChannelIdOrderByCreatedAtDesc(@Param("channelId") Long channelId);

    @Query("""
            select m from Message m
            where m.channel.id = :channelId and (m.deleted is null or m.deleted = false)
            order by m.createdAt asc
            """)
    List<Message> findVisibleByChannelIdOrderByCreatedAtAsc(@Param("channelId") Long channelId);
}
