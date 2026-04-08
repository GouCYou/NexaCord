package cn.cctstudio.nexacord.repository;

import cn.cctstudio.nexacord.model.Member;
import cn.cctstudio.nexacord.model.Server;
import cn.cctstudio.nexacord.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    List<Member> findByUser(User user);
    List<Member> findByServer(Server server);
    Optional<Member> findByServerAndUser(Server server, User user);
    Optional<Member> findByServerIdAndUserId(Long serverId, Long userId);
    boolean existsByServerIdAndUserId(Long serverId, Long userId);
}