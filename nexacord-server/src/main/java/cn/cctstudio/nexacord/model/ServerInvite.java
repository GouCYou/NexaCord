package cn.cctstudio.nexacord.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "server_invites", indexes = {
        @Index(name = "idx_server_invites_code", columnList = "code", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerInvite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false)
    @JsonIgnore
    private Server server;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    @JsonIgnore
    private User creator;

    private Instant expiresAt;
    private Integer maxUses;
    private Integer useCount;

    @CreationTimestamp
    private Instant createdAt;
}
