package com.otboo.team03.notification.outbox.repository;

import com.otboo.team03.notification.outbox.entity.OutboxEvent;
import com.otboo.team03.notification.outbox.entity.OutboxEventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAsc(
            OutboxEventStatus status,
            Instant availableAt,
            Pageable pageable
    );
}
