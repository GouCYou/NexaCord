package cn.cctstudio.nexacord.repository;

import cn.cctstudio.nexacord.model.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    default List<Friendship> findAcceptedByUserId(Long userId) {
        return findByUserIdAndStatus(userId, Friendship.Status.ACCEPTED);
    }

    default List<Friendship> findIncomingPendingByUserId(Long userId) {
        return findIncomingByUserIdAndStatus(userId, Friendship.Status.PENDING);
    }

    default List<Friendship> findOutgoingPendingByUserId(Long userId) {
        return findOutgoingByUserIdAndStatus(userId, Friendship.Status.PENDING);
    }

    @Query("""
            select friendship from Friendship friendship
            where friendship.status = :status
              and (friendship.requester.id = :userId or friendship.addressee.id = :userId)
            order by friendship.updatedAt desc
            """)
    List<Friendship> findByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") Friendship.Status status
    );

    @Query("""
            select friendship from Friendship friendship
            where friendship.status = :status
              and friendship.addressee.id = :userId
            order by friendship.createdAt desc
            """)
    List<Friendship> findIncomingByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") Friendship.Status status
    );

    @Query("""
            select friendship from Friendship friendship
            where friendship.status = :status
              and friendship.requester.id = :userId
            order by friendship.createdAt desc
            """)
    List<Friendship> findOutgoingByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") Friendship.Status status
    );

    @Query("""
            select friendship from Friendship friendship
            where (friendship.requester.id = :userId and friendship.addressee.id = :otherUserId)
               or (friendship.requester.id = :otherUserId and friendship.addressee.id = :userId)
            """)
    Optional<Friendship> findBetweenUsers(
            @Param("userId") Long userId,
            @Param("otherUserId") Long otherUserId
    );
}
