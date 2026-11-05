package com.otboo.team03.directmessage.entity;

import com.otboo.team03.global.domain.CreateEntity;
import com.otboo.team03.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "direct_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DirectMessage extends CreateEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "sender_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_users_to_direct_message_sender")
    )
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "receiver_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_users_to_direct_message_receiver")
    )
    private User receiver;

    @Column(name = "dm_key", nullable = false, length = 73)
    private String dmKey;

    @Column(nullable = false, length = 1_000)
    private String content;

    public DirectMessage(User sender, User receiver, String content) {
        this.sender = Objects.requireNonNull(sender);
        this.receiver = Objects.requireNonNull(receiver);
        this.dmKey = createDmKey(sender.getId(), receiver.getId());
        this.content = Objects.requireNonNull(content);
    }

    public static String createDmKey(UUID firstUserId, UUID secondUserId) {
        Objects.requireNonNull(firstUserId);
        Objects.requireNonNull(secondUserId);

        return firstUserId.toString().compareTo(secondUserId.toString()) < 0
                ? firstUserId + "_" + secondUserId
                : secondUserId + "_" + firstUserId;
    }
}
