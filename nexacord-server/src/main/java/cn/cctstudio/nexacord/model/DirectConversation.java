package cn.cctstudio.nexacord.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Set;

@Entity
@Table(
        name = "direct_conversations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_direct_conversation_users", columnNames = {"user_one_id", "user_two_id"})
        },
        indexes = {
                @Index(name = "idx_direct_conversations_user_one", columnList = "user_one_id"),
                @Index(name = "idx_direct_conversations_user_two", columnList = "user_two_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectConversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_one_id")
    private User userOne;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_two_id")
    private User userTwo;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL)
    @JsonIgnore
    private Set<DirectMessage> messages;
}
