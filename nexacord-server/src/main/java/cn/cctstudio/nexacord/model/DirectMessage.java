package cn.cctstudio.nexacord.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "direct_messages",
        indexes = {
                @Index(name = "idx_direct_messages_conversation", columnList = "conversation_id"),
                @Index(name = "idx_direct_messages_author", columnList = "author_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String content;

    @ManyToOne(optional = false)
    @JoinColumn(name = "conversation_id")
    @JsonIgnoreProperties({"messages"})
    private DirectConversation conversation;

    @ManyToOne(optional = false)
    @JoinColumn(name = "author_id")
    @JsonIgnoreProperties({
            "password",
            "authorities",
            "accountNonExpired",
            "accountNonLocked",
            "credentialsNonExpired",
            "enabled",
            "members",
            "messages"
    })
    private User author;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Column(name = "is_edited", nullable = false)
    @Builder.Default
    private Boolean edited = Boolean.FALSE;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean deleted = Boolean.FALSE;
}
