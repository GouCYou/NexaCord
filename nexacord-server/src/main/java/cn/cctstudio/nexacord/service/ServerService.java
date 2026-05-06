package cn.cctstudio.nexacord.service;

import cn.cctstudio.nexacord.model.Server;
import cn.cctstudio.nexacord.model.User;

import java.util.List;

public interface ServerService {
    Server createServer(Server server, User owner);
    Server getServerById(Long id);
    List<Server> searchServersByName(String name);
    List<Server> getServersByUser(User user);
    Server updateServer(Server server);
    void deleteServer(Long id);
    boolean isServerOwner(Long serverId, Long userId);
    boolean isServerManager(Long serverId, Long userId);
    boolean isServerMember(Long serverId, Long userId);
}
