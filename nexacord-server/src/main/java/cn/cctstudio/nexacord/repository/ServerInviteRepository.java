package cn.cctstudio.nexacord.repository;

import cn.cctstudio.nexacord.model.ServerInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServerInviteRepository extends JpaRepository<ServerInvite, Long> {
    @Query("""
            select invite from ServerInvite invite
            join fetch invite.server
            join fetch invite.creator
            left join fetch invite.targetChannel targetChannel
            left join fetch targetChannel.server
            where invite.code = :code
            """)
    Optional<ServerInvite> findByCodeWithServerAndCreator(@Param("code") String code);

    boolean existsByCode(String code);
}
