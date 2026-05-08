package cn.cctstudio.nexacord.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "direct_attachments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    private String fileType;
    private Long fileSize;

    @Column(nullable = false)
    private String url;

    @ManyToOne(optional = false)
    @JoinColumn(name = "direct_message_id")
    @JsonIgnore
    private DirectMessage message;
}
