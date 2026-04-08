package cn.cctstudio.nexacord.service.impl;

import cn.cctstudio.nexacord.exception.ResourceNotFoundException;
import cn.cctstudio.nexacord.model.Member;
import cn.cctstudio.nexacord.model.Server;
import cn.cctstudio.nexacord.model.User;
import cn.cctstudio.nexacord.repository.MemberRepository;
import cn.cctstudio.nexacord.repository.ServerRepository;
import cn.cctstudio.nexacord.service.ServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ServerServiceImpl implements ServerService {
    private final ServerRepository serverRepository;
    private final MemberRepository memberRepository;

    @Override
    public Server createServer(Server server, User owner) {
        Server savedServer = serverRepository.save(server);
        
        // 创建服务器成员关系，将创建者设为拥有者
        Member member = Member.builder()
                .user(owner)
                .server(savedServer)
                .role(Member.Role.OWNER)
                .build();
        memberRepository.save(member);
        
        return savedServer;
    }

    @Override
    public Server getServerById(Long id) {
        return serverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Server not found with id: " + id));
    }

    @Override
    public List<Server> searchServersByName(String name) {
        return serverRepository.findByNameContaining(name);
    }

    @Override
    public List<Server> getServersByUser(User user) {
        // 通过成员关系获取用户加入的服务器
        return memberRepository.findByUser(user)
                .stream()
                .map(Member::getServer)
                .toList();
    }

    @Override
    public Server updateServer(Server server) {
        Server existingServer = getServerById(server.getId());
        existingServer.setName(server.getName());
        existingServer.setIconUrl(server.getIconUrl());
        existingServer.setBannerUrl(server.getBannerUrl());
        existingServer.setDescription(server.getDescription());
        return serverRepository.save(existingServer);
    }

    @Override
    public void deleteServer(Long id) {
        Server server = getServerById(id);
        serverRepository.delete(server);
    }

    @Override
    public boolean isServerOwner(Long serverId, Long userId) {
        Optional<Member> memberOptional = memberRepository.findByServerIdAndUserId(serverId, userId);
        return memberOptional.isPresent() && memberOptional.get().getRole() == Member.Role.OWNER;
    }
}