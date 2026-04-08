package cn.cctstudio.nexacord.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "messages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @ManyToOne
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

    @ManyToOne
    @JoinColumn(name = "channel_id")
    @JsonIgnoreProperties({"messages"})
    private Channel channel;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL)
    @JsonIgnoreProperties({"message"})
    @Builder.Default
    private Set<Attachment> attachments = new LinkedHashSet<>();

    @Column(name = "is_edited", nullable = false)
    @Builder.Default
    private Boolean edited = Boolean.FALSE;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean deleted = Boolean.FALSE;
}
