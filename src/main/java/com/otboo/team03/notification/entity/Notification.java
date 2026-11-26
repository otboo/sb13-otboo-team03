package com.otboo.team03.notification.entity;

import com.otboo.team03.global.domain.CreateEntity;
import com.otboo.team03.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends CreateEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "receiver_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_users_to_notifications_receiver")
    )
    private User receiver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "actor_id",
            foreignKey = @ForeignKey(name = "fk_users_to_notifications_actor")
    )
    private User actor;

    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType;

    @Column(name = "related_id")
    private UUID relatedId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationLevel level;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "source_event_id", nullable = false)
    private UUID sourceEventId;

    public Notification(
            User receiver,
            User actor,
            String notificationType,
            UUID relatedId,
            String title,
            String content,
            NotificationLevel level,
            UUID sourceEventId
    ) {
        this.receiver = receiver;
        this.actor = actor;
        this.notificationType = notificationType;
        this.relatedId = relatedId;
        this.title = title;
        this.content = content;
        this.level = level;
        this.sourceEventId = sourceEventId;
    }

    public void markAsRead(Instant readAt) {
        if (this.readAt == null) {
            this.readAt = readAt;
        }
    }
}
