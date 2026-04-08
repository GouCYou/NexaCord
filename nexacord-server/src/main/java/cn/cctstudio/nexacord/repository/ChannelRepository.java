package cn.cctstudio.nexacord.repository;

import cn.cctstudio.nexacord.model.Channel;
import cn.cctstudio.nexacord.model.Server;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, Long> {
    List<Channel> findByServer(Server server);
    List<Channel> findByServerId(Long serverId);
}