package com.otboo.team03.weather.service;

import com.otboo.team03.notification.entity.NotificationLevel;
import com.otboo.team03.notification.event.NotificationEvent;
import com.otboo.team03.notification.event.NotificationEventType;
import com.otboo.team03.notification.outbox.service.OutboxEventPublisher;
import com.otboo.team03.profile.entity.Profile;
import com.otboo.team03.profile.repository.ProfileRepository;
import com.otboo.team03.weather.entity.Weather;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherAlertServiceImpl implements WeatherAlertService {

  private final WeatherChangeDetector weatherChangeDetector;
  private final ProfileRepository profileRepository;
  private final OutboxEventPublisher outboxEventPublisher;

  @Override
  @Transactional
  public void checkAndSendAlerts(Double latitude, Double longitude, List<Weather> weathers) {
    // 1. 날씨 급변(강수 발생, 기온 5도 이상 급상승) 감지
    Optional<WeatherAlertInfo> alertInfoOpt = weatherChangeDetector.detect(weathers);
    if (alertInfoOpt.isEmpty()) {
      return;
    }

    WeatherAlertInfo alertInfo = alertInfoOpt.get();
    log.info(">>>>> [WeatherAlert] 날씨 급변 감지됨: {} (좌표: lat={}, lon={})", alertInfo.title(), latitude, longitude);

    // 2. 해당 거점 좌표에 등록된 사용자 프로필 목록 조회
    List<Profile> profiles = profileRepository.findAllByLatitudeAndLongitude(latitude, longitude);
    if (profiles == null || profiles.isEmpty()) {
      log.info(">>>>> [WeatherAlert] 해당 거점에 등록된 사용자가 없어 알림 발행을 건너뜁니다.");
      return;
    }

    // 3. 각 사용자별로 알림 이벤트(NotificationEvent)를 생성하여 Outbox로 발행
    for (Profile profile : profiles) {
      UUID receiverId = profile.getUserId();
      if (receiverId == null) {
        continue;
      }

      NotificationEvent event = new NotificationEvent(
          UUID.randomUUID(),                          // eventId
          NotificationEventType.WEATHER_ALERT,        // eventType
          NotificationEvent.CURRENT_SCHEMA_VERSION,   // schemaVersion
          Instant.now(),                              // occurredAt
          UUID.randomUUID(),                          // aggregateId
          receiverId,                                 // receiverId
          null,                                       // actorId (시스템 발행)
          null,                                       // relatedId
          alertInfo.title(),                          // title ("비/눈 예보 알림" 등)
          alertInfo.content(),                        // content (안내 메시지 본문)
          NotificationLevel.INFO                      // level
      );

      outboxEventPublisher.publish(event, "WEATHER", receiverId.toString());
    }

    log.info(">>>>> [WeatherAlert] {}명의 사용자에게 날씨 급변 알림 Outbox 발행 완료", profiles.size());
  }

}