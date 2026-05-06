package cn.cctstudio.nexacord.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "friendships",
        indexes = {
                @Index(name = "idx_friendships_requester", columnList = "requester_id"),
                @Index(name = "idx_friendships_addressee", columnList = "addressee_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Friendship {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "requester_id")
    @JsonIgnore
    private User requester;

    @ManyToOne(optional = false)
    @JoinColumn(name = "addressee_id")
    @JsonIgnore
    private User addressee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public enum Status {
        PENDING, ACCEPTED, BLOCKED
    }
}
