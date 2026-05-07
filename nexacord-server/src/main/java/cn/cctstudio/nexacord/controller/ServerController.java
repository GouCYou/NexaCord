package cn.cctstudio.nexacord.controller;

import cn.cctstudio.nexacord.dto.MemberResponse;
import cn.cctstudio.nexacord.dto.MemberRoleUpdateRequest;
import cn.cctstudio.nexacord.dto.ServerInviteResponse;
import cn.cctstudio.nexacord.exception.AccessDeniedException;
import cn.cctstudio.nexacord.exception.BadRequestException;
import cn.cctstudio.nexacord.exception.ResourceNotFoundException;
import cn.cctstudio.nexacord.model.Member;
import cn.cctstudio.nexacord.model.Server;
import cn.cctstudio.nexacord.model.ServerInvite;
import cn.cctstudio.nexacord.model.User;
import cn.cctstudio.nexacord.repository.MemberRepository;
import cn.cctstudio.nexacord.repository.ServerInviteRepository;
import cn.cctstudio.nexacord.repository.UserRepository;
import cn.cctstudio.nexacord.service.ServerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/servers")
@RequiredArgsConstructor
public class ServerController {
    private final ServerService serverService;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ServerInviteRepository serverInviteRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping
    public ResponseEntity<Server> createServer(@Valid @RequestBody Server server, @AuthenticationPrincipal User currentUser) {
        Server createdServer = serverService.createServer(server, currentUser);
        return new ResponseEntity<>(createdServer, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Server> getServerById(@PathVariable Long id) {
        Server server = serverService.getServerById(id);
        return new ResponseEntity<>(server, HttpStatus.OK);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Server>> searchServersByName(@RequestParam String name) {
        List<Server> servers = serverService.searchServersByName(name);
        return new ResponseEntity<>(servers, HttpStatus.OK);
    }

    @GetMapping("/user")
    public ResponseEntity<List<Server>> getServersByUser(@AuthenticationPrincipal User currentUser) {
        List<Server> servers = serverService.getServersByUser(currentUser);
        return new ResponseEntity<>(servers, HttpStatus.OK);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Server> updateServer(@PathVariable Long id, @Valid @RequestBody Server server, @AuthenticationPrincipal User currentUser) {
        requireManager(id, currentUser);
        server.setId(id);
        Server updatedServer = serverService.updateServer(server);
        return new ResponseEntity<>(updatedServer, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteServer(@PathVariable Long id, @AuthenticationPrincipal User currentUser) {
        // 检查用户是否是服务器所有者
        if (!serverService.isServerOwner(id, currentUser.getId())) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        serverService.deleteServer(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<MemberResponse>> getServerMembers(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser
    ) {
        requireMember(id, currentUser);
        return new ResponseEntity<>(
                memberRepository.findByServerIdWithUser(id)
                        .stream()
                        .map(MemberResponse::from)
                        .toList(),
                HttpStatus.OK
        );
    }

    @PostMapping("/{id}/members/{userId}")
    public ResponseEntity<MemberResponse> addServerMember(
            @PathVariable Long id,
            @PathVariable Long userId,
            @AuthenticationPrincipal User currentUser
    ) {
        requireManager(id, currentUser);

        if (memberRepository.existsByServerIdAndUserId(id, userId)) {
            throw new BadRequestException("这位用户已经在服务器里。");
        }

        Server server = serverService.getServerById(id);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("没有找到这位用户。"));

        Member member = Member.builder()
                .server(server)
                .user(user)
                .role(Member.Role.MEMBER)
                .build();

        MemberResponse response = MemberResponse.from(memberRepository.save(member));
        messagingTemplate.convertAndSend(
                "/topic/servers/update",
                Map.of(
                        "type", "MEMBER_ADDED",
                        "serverId", id,
                        "userId", userId,
                        "member", response
                )
        );
        messagingTemplate.convertAndSend(
                "/topic/servers/user/" + userId,
                Map.of(
                        "type", "MEMBER_ADDED",
                        "serverId", id,
                        "member", response
                )
        );

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PatchMapping("/{serverId}/members/{memberId}/role")
    public ResponseEntity<MemberResponse> updateMemberRole(
            @PathVariable Long serverId,
            @PathVariable Long memberId,
            @Valid @RequestBody MemberRoleUpdateRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        if (!serverService.isServerOwner(serverId, currentUser.getId())) {
            throw new AccessDeniedException("只有服务器拥有者可以管理身份组。");
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("没有找到这位服务器成员。"));
        if (!member.getServer().getId().equals(serverId)) {
            throw new BadRequestException("成员不属于当前服务器。");
        }
        if (member.getRole() == Member.Role.OWNER) {
            throw new BadRequestException("不能修改服务器拥有者的身份组。");
        }

        Member.Role role = parseAssignableRole(request.getRole());
        member.setRole(role);
        return new ResponseEntity<>(MemberResponse.from(memberRepository.save(member)), HttpStatus.OK);
    }

    @DeleteMapping("/{serverId}/members/{memberId}")
    public ResponseEntity<Void> removeServerMember(
            @PathVariable Long serverId,
            @PathVariable Long memberId,
            @AuthenticationPrincipal User currentUser
    ) {
        requireManager(serverId, currentUser);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("没有找到这位服务器成员。"));
        if (!member.getServer().getId().equals(serverId)) {
            throw new BadRequestException("成员不属于当前服务器。");
        }
        if (member.getRole() == Member.Role.OWNER) {
            throw new BadRequestException("不能移除服务器拥有者。");
        }

        memberRepository.delete(member);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/{id}/invites")
    public ResponseEntity<ServerInviteResponse> createServerInvite(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser
    ) {
        requireMember(id, currentUser);

        Server server = serverService.getServerById(id);
        ServerInvite invite = ServerInvite.builder()
                .code(generateInviteCode())
                .server(server)
                .creator(currentUser)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .maxUses(null)
                .useCount(0)
                .build();

        return new ResponseEntity<>(
                ServerInviteResponse.from(serverInviteRepository.save(invite)),
                HttpStatus.CREATED
        );
    }

    private void requireMember(Long serverId, User currentUser) {
        if (currentUser == null || !serverService.isServerMember(serverId, currentUser.getId())) {
            throw new AccessDeniedException("你不是这个服务器的成员。");
        }
    }

    private void requireManager(Long serverId, User currentUser) {
        if (currentUser == null || !serverService.isServerManager(serverId, currentUser.getId())) {
            throw new AccessDeniedException("你没有管理这个服务器的权限。");
        }
    }

    private Member.Role parseAssignableRole(String value) {
        try {
            Member.Role role = Member.Role.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (role == Member.Role.OWNER || role == Member.Role.MODERATOR) {
                throw new IllegalArgumentException();
            }
            return role;
        } catch (RuntimeException ex) {
            throw new BadRequestException("当前只支持设置为管理员或成员。");
        }
    }

    private String generateInviteCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        } while (serverInviteRepository.existsByCode(code));

        return code;
    }
}
