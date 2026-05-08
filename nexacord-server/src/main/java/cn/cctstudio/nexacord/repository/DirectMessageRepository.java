package cn.cctstudio.nexacord.repository;

import cn.cctstudio.nexacord.model.DirectMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {
    @Query("""
            select distinct message from DirectMessage message
            join fetch message.author
            left join fetch message.attachments
            where message.conversation.id = :conversationId and (message.deleted is null or message.deleted = false)
            order by message.createdAt asc
            """)
    List<DirectMessage> findVisibleByConversationIdOrderByCreatedAtAsc(@Param("conversationId") Long conversationId);

    @Query("""
            select distinct message from DirectMessage message
            join fetch message.author
            left join fetch message.attachments
            where message.conversation.id = :conversationId and (message.deleted is null or message.deleted = false)
            order by message.createdAt desc
            """)
    List<DirectMessage> findVisibleByConversationIdOrderByCreatedAtDesc(@Param("conversationId") Long conversationId);

    @Query("""
            select distinct message from DirectMessage message
            join fetch message.author
            join fetch message.conversation
            left join fetch message.attachments
            where message.id = :id
            """)
    Optional<DirectMessage> findWithAttachmentsById(@Param("id") Long id);

    @Query("""
            select distinct message from DirectMessage message
            join fetch message.author
            left join fetch message.attachments
            where message.conversation.id = :conversationId and (message.deleted is null or message.deleted = false)
            order by message.createdAt desc
            """)
    List<DirectMessage> findVisibleWithAttachmentsByConversationIdOrderByCreatedAtDesc(@Param("conversationId") Long conversationId);
}
