package cn.cctstudio.nexacord.repository;

import cn.cctstudio.nexacord.model.DirectConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DirectConversationRepository extends JpaRepository<DirectConversation, Long> {
    @Query("""
            select conversation from DirectConversation conversation
            join fetch conversation.userOne
            join fetch conversation.userTwo
            where conversation.id = :id
            """)
    Optional<DirectConversation> findWithUsersById(@Param("id") Long id);

    @Query("""
            select conversation from DirectConversation conversation
            join fetch conversation.userOne
            join fetch conversation.userTwo
            where conversation.userOne.id = :userId or conversation.userTwo.id = :userId
            order by conversation.updatedAt desc
            """)
    List<DirectConversation> findByUserId(@Param("userId") Long userId);

    @Query("""
            select conversation from DirectConversation conversation
            join fetch conversation.userOne
            join fetch conversation.userTwo
            where (conversation.userOne.id = :userId and conversation.userTwo.id = :otherUserId)
               or (conversation.userOne.id = :otherUserId and conversation.userTwo.id = :userId)
            """)
    Optional<DirectConversation> findBetweenUsers(
            @Param("userId") Long userId,
            @Param("otherUserId") Long otherUserId
    );
}
