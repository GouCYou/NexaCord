package cn.cctstudio.nexacord.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "members")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonIgnore // 忽略循环引用
    private User user;

    @ManyToOne
    @JoinColumn(name = "server_id")
    @JsonIgnore // 忽略循环引用
    private Server server;

    private String nickname;
    private String avatarUrl;

    @CreationTimestamp
    private Instant joinedAt;

    @Enumerated(EnumType.STRING)
    private Role role;

    public enum Role {
        OWNER, ADMIN, MODERATOR, MEMBER
    }
}