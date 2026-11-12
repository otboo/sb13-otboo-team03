package com.otboo.team03.notification.repository;

import com.otboo.team03.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("""
            select n
            from Notification n
            where n.receiver.id = :receiverId
              and (
                    n.createdAt < :cursor
                    or (n.createdAt = :cursor and n.id < :idAfter)
              )
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> findPageByReceiverIdAfterCursor(
            @Param("receiverId") UUID receiverId,
            @Param("cursor") Instant cursor,
            @Param("idAfter") UUID idAfter,
            Pageable pageable
    );

    List<Notification> findByReceiver_IdOrderByCreatedAtDescIdDesc(
            UUID receiverId,
            Pageable pageable
    );

    long countByReceiver_Id(UUID receiverId);

    boolean existsByReceiver_IdAndNotificationTypeAndSourceEventId(
            UUID receiverId,
            String notificationType,
            UUID sourceEventId
    );

    Optional<Notification> findByIdAndReceiver_Id(UUID notificationId, UUID receiverId);

    @Query("""
            select n
            from Notification n
            where n.receiver.id = :receiverId
              and (
                    n.createdAt > :createdAt
                    or (n.createdAt = :createdAt and n.id > :lastEventId)
              )
            order by n.createdAt asc, n.id asc
            """)
    List<Notification> findByReceiverIdAfterEvent(
            @Param("receiverId") UUID receiverId,
            @Param("createdAt") Instant createdAt,
            @Param("lastEventId") UUID lastEventId
    );
}
