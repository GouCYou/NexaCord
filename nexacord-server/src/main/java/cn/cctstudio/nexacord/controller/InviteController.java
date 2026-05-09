package cn.cctstudio.nexacord.controller;

import cn.cctstudio.nexacord.dto.ServerInviteResponse;
import cn.cctstudio.nexacord.exception.BadRequestException;
import cn.cctstudio.nexacord.exception.ResourceNotFoundException;
import cn.cctstudio.nexacord.model.Channel;
import cn.cctstudio.nexacord.model.Member;
import cn.cctstudio.nexacord.model.Server;
import cn.cctstudio.nexacord.model.ServerInvite;
import cn.cctstudio.nexacord.model.User;
import cn.cctstudio.nexacord.repository.MemberRepository;
import cn.cctstudio.nexacord.repository.ServerInviteRepository;
import cn.cctstudio.nexacord.service.ServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/invites")
@RequiredArgsConstructor
public class InviteController {
    private final ServerInviteRepository serverInviteRepository;
    private final MemberRepository memberRepository;
    private final ServerService serverService;

    @GetMapping("/{code}")
    public ResponseEntity<ServerInviteResponse> getInvite(
            @PathVariable String code,
            @AuthenticationPrincipal User currentUser
    ) {
        ServerInvite invite = findValidInvite(code);
        boolean isMember = currentUser != null
                && memberRepository.existsByServerIdAndUserId(invite.getServer().getId(), currentUser.getId());
        return new ResponseEntity<>(ServerInviteResponse.from(invite, isMember), HttpStatus.OK);
    }

    @PostMapping("/{code}/join")
    public ResponseEntity<Server> joinByInvite(
            @PathVariable String code,
            @AuthenticationPrincipal User currentUser
    ) {
        ServerInvite invite = findValidInvite(code);
        Server server = invite.getServer();
        Channel targetChannel = invite.getTargetChannel();
        if (targetChannel != null && !targetChannel.getServer().getId().equals(server.getId())) {
            throw new BadRequestException("邀请目标频道不属于当前服务器。");
        }

        if (!serverService.isServerMember(server.getId(), currentUser.getId())) {
            Member member = Member.builder()
                    .server(server)
                    .user(currentUser)
                    .role(Member.Role.MEMBER)
                    .build();
            memberRepository.save(member);
            invite.setUseCount((invite.getUseCount() == null ? 0 : invite.getUseCount()) + 1);
            serverInviteRepository.save(invite);
        }

        return new ResponseEntity<>(server, HttpStatus.OK);
    }

    private ServerInvite findValidInvite(String code) {
        ServerInvite invite = serverInviteRepository.findByCodeWithServerAndCreator(code)
                .orElseThrow(() -> new ResourceNotFoundException("邀请链接不存在或已经失效。"));

        if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("邀请链接已经过期。");
        }

        if (
                invite.getMaxUses() != null
                        && invite.getUseCount() != null
                        && invite.getUseCount() >= invite.getMaxUses()
        ) {
            throw new BadRequestException("邀请链接已经达到使用次数上限。");
        }

        return invite;
    }
}
