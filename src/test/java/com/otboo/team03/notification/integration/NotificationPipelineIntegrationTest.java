package com.otboo.team03.notification.integration;

import com.otboo.team03.notification.dto.response.NotificationDto;
import com.otboo.team03.notification.entity.Notification;
import com.otboo.team03.notification.entity.NotificationLevel;
import com.otboo.team03.notification.event.NotificationEvent;
import com.otboo.team03.notification.event.NotificationEventType;
import com.otboo.team03.notification.outbox.entity.OutboxEvent;
import com.otboo.team03.notification.outbox.entity.OutboxEventStatus;
import com.otboo.team03.notification.outbox.repository.OutboxEventRepository;
import com.otboo.team03.notification.outbox.relay.OutboxEventRelayService;
import com.otboo.team03.notification.outbox.relay.OutboxEventRelayScheduler;
import com.otboo.team03.notification.repository.NotificationRepository;
import com.otboo.team03.notification.sse.SseConnectionManager;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import java.util.Map;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class NotificationPipelineIntegrationTest {

    private static final String NOTIFICATION_TOPIC =
            "otboo.notification.events.pipeline-test";
    private static final String CONSUMER_GROUP =
            "notification-pipeline-test";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer("apache/kafka-native:3.8.0");

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> CONSUMER_GROUP);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.listener.auto-startup", () -> true);
        registry.add("otboo.kafka.notification-topic", () -> NOTIFICATION_TOPIC);
        registry.add("otboo.kafka.notification-dlt-topic", () -> NOTIFICATION_TOPIC + ".DLT");
        registry.add("spring.task.scheduling.enabled", () -> false);
    }

    @BeforeAll
    static void createNotificationTopic() throws Exception {
        try (Admin admin = Admin.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers()
        ))) {
            admin.createTopics(List.of(new NewTopic(NOTIFICATION_TOPIC, 1, (short) 1)))
                    .all()
                    .get(30, TimeUnit.SECONDS);
        }
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OutboxEventRelayService outboxEventRelayService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OutboxEventRelayScheduler outboxEventRelayScheduler;

    @MockitoBean
    private SseConnectionManager sseConnectionManager;

    @Test
    void Outbox부터_SSE까지_알림_파이프라인을_통합검증한다() throws Exception {
        User receiver = userRepository.saveAndFlush(new User(
                UUID.randomUUID() + "@example.com",
                "encoded-password",
                "수신 사용자"
        ));
        User actor = userRepository.saveAndFlush(new User(
                UUID.randomUUID() + "@example.com",
                "encoded-password",
                "행위 사용자"
        ));

        NotificationEvent notificationEvent = notificationEvent(
                receiver.getId(),
                actor.getId()
        );
        Instant createdAt = Instant.now().minusSeconds(1);
        OutboxEvent outboxEvent = new OutboxEvent(
                notificationEvent.eventId(),
                notificationEvent.eventType().name(),
                (short) notificationEvent.schemaVersion(),
                "DIRECT_MESSAGE",
                notificationEvent.aggregateId(),
                receiver.getId().toString(),
                objectMapper.readTree(objectMapper.writeValueAsString(notificationEvent)),
                notificationEvent.occurredAt(),
                createdAt
        );

        saveOutboxEvent(outboxEvent);

        int relayedCount = outboxEventRelayService.relayPendingEvents(1);

        assertThat(relayedCount).isEqualTo(1);
        OutboxEvent relayedOutboxEvent = outboxEventRepository
                .findById(notificationEvent.eventId())
                .orElseThrow();
        assertThat(relayedOutboxEvent.getStatus())
                .as("Outbox 발행 실패 원인: %s", relayedOutboxEvent.getLastError())
                .isEqualTo(OutboxEventStatus.PUBLISHED);

        List<Notification> savedNotifications = awaitNotifications(
                receiver.getId(),
                notificationEvent.eventId()
        );

        assertThat(savedNotifications).hasSize(1);

        Notification savedNotification = savedNotifications.get(0);
        assertThat(savedNotification.getNotificationType())
                .isEqualTo(NotificationEventType.DIRECT_MESSAGE_RECEIVED.name());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT actor_id FROM notifications WHERE id = ?",
                UUID.class,
                savedNotification.getId()
        )).isEqualTo(actor.getId());

        var deliveredNotification = org.mockito.ArgumentCaptor
                .forClass(NotificationDto.class);
        verify(sseConnectionManager, timeout(20_000)).send(
                eq(receiver.getId()),
                deliveredNotification.capture()
        );
        assertThat(deliveredNotification.getValue())
                .satisfies(notification -> {
                    assertThat(notification.receiverId()).isEqualTo(receiver.getId());
                    assertThat(notification.title()).isEqualTo("새 메시지");
                    assertThat(notification.content())
                            .isEqualTo("새로운 메시지가 도착했습니다.");
                    assertThat(notification.level()).isEqualTo(NotificationLevel.INFO);
                });
    }

    private OutboxEvent saveOutboxEvent(OutboxEvent outboxEvent) {
        return outboxEventRepository.saveAndFlush(outboxEvent);
    }

    private NotificationEvent notificationEvent(UUID receiverId, UUID actorId) {
        return new NotificationEvent(
                UUID.randomUUID(),
                NotificationEventType.DIRECT_MESSAGE_RECEIVED,
                NotificationEvent.CURRENT_SCHEMA_VERSION,
                Instant.now(),
                UUID.randomUUID(),
                receiverId,
                actorId,
                UUID.randomUUID(),
                "새 메시지",
                "새로운 메시지가 도착했습니다.",
                NotificationLevel.INFO
        );
    }

    private List<Notification> awaitNotifications(UUID receiverId, UUID eventId)
            throws InterruptedException {
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(30);

        while (System.nanoTime() < deadline) {
            List<Notification> notifications = notificationRepository
                    .findByReceiver_IdOrderByCreatedAtDescIdDesc(
                            receiverId,
                            PageRequest.of(0, 20)
                    );

            List<Notification> matchingNotifications = notifications.stream()
                    .filter(notification -> eventId.equals(notification.getSourceEventId()))
                    .toList();
            if (!matchingNotifications.isEmpty()) {
                return matchingNotifications;
            }

            Thread.sleep(100);
        }

        fail("Kafka Consumer가 알림 DB에 저장한 결과를 제한 시간 안에 확인하지 못했습니다.");
        return List.of();
    }
}
